package com.andryoga.safebox.security.envelope.migration

import android.database.sqlite.SQLiteException
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.CrashReporter
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.AuthenticatorDataDao
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.di.IsDebug
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.SecurityUtils
import com.andryoga.safebox.security.envelope.KdfParams
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-encrypts a `crypto_version = 1` vault under a newly minted 32-byte DEK (`crypto_version = 2`).
 *
 * Legacy records are decrypted into memory first (allowing bounded retries on transient Keystore
 * errors outside any SQLite transaction), then re-encrypted, verified, and committed atomically in
 * a single Room database transaction.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@Singleton
class LegacyVaultMigrator @Inject constructor(
    private val database: Lazy<SafeBoxDatabase>,
    private val loginDataDao: Lazy<LoginDataDao>,
    private val bankAccountDataDao: Lazy<BankAccountDataDao>,
    private val bankCardDataDao: Lazy<BankCardDataDao>,
    private val secureNoteDataDao: Lazy<SecureNoteDataDao>,
    private val authenticatorDataDao: Lazy<AuthenticatorDataDao>,
    private val userDetailsDao: Lazy<UserDetailsDao>,
    private val vaultMetaDao: VaultMetaDao,
    private val keystoreGateway: KeystoreGateway,
    private val envelopeEncryptionManager: EnvelopeEncryptionManager,
    private val sessionKeyHolder: SessionKeyHolder,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val workManager: Lazy<WorkManager>,
    private val progress: VaultMigrationProgress,
    private val analyticsHelper: AnalyticsHelper,
    private val crashReporter: CrashReporter,
    private val dispatchersProvider: DispatchersProvider,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:IsDebug private val isDebug: Boolean,
) {
    private val mutex = Mutex()
    private val attempts = AtomicInteger(0)

    /**
     * Debug-only fault-injection hook. When non-null in a debug build, [migrate] throws a
     * stage-appropriate fault upon reaching that stage.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @Volatile
    var debugFailStage: MigrationStage? = null

    /**
     * Migrates a `crypto_version = 1` vault to `crypto_version = 2` under [password].
     *
     * Executes inside [applicationScope] on [DispatchersProvider.default] so a configuration change
     * in the calling ViewModel never cancels an in-flight transaction.
     *
     * @param password Verified master password (for `LOGIN`) or new master password (for `FORGOT_PASSWORD`); zeroed before returning.
     * @param newHint Replacement plaintext hint when [trigger] is [MigrationTrigger.FORGOT_PASSWORD], or `null` to decrypt the existing legacy hint.
     * @param trigger Initiating user flow (`LOGIN` or `FORGOT_PASSWORD`).
     * @return [MigrationOutcome] describing success, no-op, or structured failure.
     */
    suspend fun migrate(
        password: CharArray,
        newHint: String? = null,
        trigger: MigrationTrigger = MigrationTrigger.LOGIN,
    ): MigrationOutcome {
        val passwordCopy = password.clone()
        password.fill('\u0000')
        return try {
            withContext(applicationScope.coroutineContext + dispatchersProvider.default) {
                mutex.withLock {
                    runMigrationLocked(passwordCopy, newHint, trigger)
                }
            }
        } finally {
            passwordCopy.fill('\u0000')
        }
    }

    private suspend fun runMigrationLocked(
        password: CharArray,
        newHint: String?,
        trigger: MigrationTrigger,
    ): MigrationOutcome {
        if (vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) == CryptoVersion.ENVELOPE) {
            return MigrationOutcome.AlreadyMigrated
        }

        val logins = loginDataDao.get().getAllEntities()
        val accounts = bankAccountDataDao.get().getAllEntities()
        val cards = bankCardDataDao.get().getAllEntities()
        val notes = secureNoteDataDao.get().getAllEntities()
        val authenticators = authenticatorDataDao.get().getAllEntities()
        val totalRecords = logins.size + accounts.size + cards.size + notes.size + authenticators.size

        val attempt = attempts.incrementAndGet()
        val ctx = MigrationRunContext(
            startTimeMs = System.currentTimeMillis(),
            totalRecords = totalRecords,
            attempt = attempt,
            trigger = trigger,
        )

        crashReporter.setKey(KEY_CRYPTO_VERSION, 1)
        crashReporter.setKey(KEY_MIGRATION_ATTEMPT, attempt)
        analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_START) {
            param(AnalyticsParam.TRIGGER, trigger.wireName)
            param(AnalyticsParam.RECORD_COUNT, totalRecords)
            param(AnalyticsParam.ATTEMPT, attempt)
        }
        progress.start(totalRecords)

        var dek: ByteArray? = null
        try {
            // Stage 1: Load legacy Keystore key with transient retry.
            enterStage(ctx, MigrationStage.LOAD_LEGACY_KEY)
            val legacyKey = retryTransientKeystore(ctx) {
                keystoreGateway.legacyVaultKey()
            } ?: return recordFailure(
                ctx = ctx,
                stage = MigrationStage.LOAD_LEGACY_KEY,
                reason = MigrationFailureReason.LEGACY_KEY_LOST,
                cause = null,
            )

            // Stage 2: Mint DEK, derive KDF parameters, wrap under password, and create verifier.
            enterStage(ctx, MigrationStage.DERIVE_KDF)
            val mintedDek = envelopeEncryptionManager.mintDek()
            dek = mintedDek
            val kdfParams = envelopeEncryptionManager.deriveKdfParams()
            val wrappedPassword = envelopeEncryptionManager.wrapWithPassword(
                password = password.clone(),
                params = kdfParams,
                dek = mintedDek,
            )
            val verifier = envelopeEncryptionManager.makeVerifier(mintedDek)

            val (wrappedDevice, wrappedBiometric) = createOptionalKeystoreWraps(mintedDek)

            // Stage 3: Decrypt all record tables and legacy hint into memory before opening the DB transaction.
            val decryptedLogins = decryptLogins(ctx, legacyKey, logins)
            val decryptedAccounts = decryptBankAccounts(ctx, legacyKey, accounts)
            val decryptedCards = decryptBankCards(ctx, legacyKey, cards)
            val decryptedNotes = decryptSecureNotes(ctx, legacyKey, notes)
            val decryptedAuthenticators = decryptAuthenticators(ctx, legacyKey, authenticators)
            val resolvedHint = resolvePlaintextHint(ctx, legacyKey, newHint)

            // Stage 4: Re-encrypt, verify row counts and password wrap, and commit vault_meta in one transaction.
            val dekSecretKey = SecretKeySpec(mintedDek, AES_ALGORITHM)
            val encodedKdfParams = kdfParams.encode()
            database.get().withTransaction {
                reencryptLogins(ctx, dekSecretKey, decryptedLogins)
                reencryptBankAccounts(ctx, dekSecretKey, decryptedAccounts)
                reencryptBankCards(ctx, dekSecretKey, decryptedCards)
                reencryptSecureNotes(ctx, dekSecretKey, decryptedNotes)
                reencryptAuthenticators(ctx, dekSecretKey, decryptedAuthenticators)

                verifyTableCounts(
                    ctx = ctx,
                    loginsBefore = logins.size,
                    loginsDropped = decryptedLogins.corruptedKeys.size,
                    accountsBefore = accounts.size,
                    accountsDropped = decryptedAccounts.corruptedKeys.size,
                    cardsBefore = cards.size,
                    cardsDropped = decryptedCards.corruptedKeys.size,
                    notesBefore = notes.size,
                    notesDropped = decryptedNotes.corruptedKeys.size,
                    authenticatorsBefore = authenticators.size,
                    authenticatorsDropped = decryptedAuthenticators.corruptedKeys.size,
                )

                verifyPasswordWrapRoundTrip(
                    ctx = ctx,
                    password = password,
                    encodedKdfParams = encodedKdfParams,
                    wrappedPassword = wrappedPassword,
                    mintedDek = mintedDek,
                    verifier = verifier,
                )

                if (resolvedHint != null) {
                    userDetailsDao.get().updateHint(resolvedHint, Date())
                }

                commitVaultMeta(
                    ctx = ctx,
                    encodedKdfParams = encodedKdfParams,
                    wrappedPassword = wrappedPassword,
                    verifier = verifier,
                    wrappedDevice = wrappedDevice,
                    wrappedBiometric = wrappedBiometric,
                )
            }

            // Stage 5: Open DEK session, cancel pre-upgrade workers, and remove legacy Keystore key.
            sessionKeyHolder.setSessionKey(mintedDek.clone())
            runCatching {
                workManager.get().cancelUniqueWork(CommonConstants.WORKER_NAME_BACKUP_DATA)
                workManager.get().cancelUniqueWork(CommonConstants.WORKER_NAME_RESTORE_DATA)
            }

            crashReporter.setKey(KEY_CRYPTO_VERSION, 2)
            keystoreGateway.deleteLegacyVaultKey()
            attempts.set(0)

            val durationMs = (System.currentTimeMillis() - ctx.startTimeMs).coerceAtLeast(0L)
            val wrapsValue = buildWrapsParam(
                hasDeviceWrap = wrappedDevice != null,
                hasBiometricWrap = wrappedBiometric != null,
            )
            analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_SUCCESS) {
                param(AnalyticsParam.DURATION_MS, durationMs)
                param(AnalyticsParam.RECORD_COUNT, totalRecords)
                param(AnalyticsParam.RETRIES, ctx.retriesCount)
                param(AnalyticsParam.LEGACY_DECRYPT_MS, ctx.legacyDecryptNanos / NANOS_PER_MILLI)
                param(AnalyticsParam.WRAPS, wrapsValue)
                param(AnalyticsParam.ATTEMPT, attempt)
            }
            return MigrationOutcome.Success(dek = mintedDek)
        } catch (c: CancellationException) {
            dek?.fill(0)
            throw c
        } catch (v: VerifyMismatchException) {
            dek?.fill(0)
            return recordFailure(
                ctx = ctx,
                stage = v.stage,
                reason = MigrationFailureReason.VERIFY_MISMATCH,
                cause = v.cause ?: v,
            )
        } catch (e: Exception) {
            dek?.fill(0)
            val (stage, reason) = classifyException(ctx.currentStage, e)
            return recordFailure(
                ctx = ctx,
                stage = stage,
                reason = reason,
                cause = e,
            )
        } finally {
            progress.reset()
        }
    }

    private suspend fun createOptionalKeystoreWraps(dek: ByteArray): Pair<String?, String?> {
        val securitySnapshot = runCatching { deviceSecurityStatus.snapshot() }.getOrNull()
        val biometricDisabled =
            vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) == BIOMETRIC_DISABLED_VALUE
        val deviceSuffix = vaultMetaDao.get(VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX)
        val biometricSuffix = vaultMetaDao.get(VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX)

        val wrappedDevice = if (securitySnapshot?.isDeviceSecure == true) {
            tryCreateOptionalWrap(WrapKeySpec.DEVICE, dek, deviceSuffix)
        } else {
            null
        }
        val wrappedBiometric = if (
            securitySnapshot?.biometricClass == BiometricClass.STRONG && !biometricDisabled
        ) {
            tryCreateOptionalWrap(WrapKeySpec.BIOMETRIC, dek, biometricSuffix)
        } else {
            null
        }
        return wrappedDevice to wrappedBiometric
    }

    private suspend fun decryptLogins(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        rows: List<LoginDataEntity>,
    ): DecryptedTableResult<DecryptedLogin> {
        ctx.currentTable = TABLE_LOGIN
        val decrypted = ArrayList<DecryptedLogin>(rows.size)
        val corruptedKeys = mutableListOf<Int>()
        for (row in rows) {
            enterStage(ctx, MigrationStage.DECRYPT_ROW, logBreadcrumb = false)
            try {
                decrypted += DecryptedLogin(
                    entity = row,
                    url = decryptNullableField(ctx, legacyKey, row.url),
                    password = decryptNullableField(ctx, legacyKey, row.password),
                    notes = decryptNullableField(ctx, legacyKey, row.notes),
                    userId = decryptField(ctx, legacyKey, row.userId),
                )
            } catch (_: CorruptedRowException) {
                corruptedKeys += row.key
            }
        }
        return DecryptedTableResult(decrypted, corruptedKeys)
    }

    private suspend fun decryptBankAccounts(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        rows: List<BankAccountDataEntity>,
    ): DecryptedTableResult<DecryptedBankAccount> {
        ctx.currentTable = TABLE_BANK_ACCOUNT
        val decrypted = ArrayList<DecryptedBankAccount>(rows.size)
        val corruptedKeys = mutableListOf<Int>()
        for (row in rows) {
            enterStage(ctx, MigrationStage.DECRYPT_ROW, logBreadcrumb = false)
            try {
                decrypted += DecryptedBankAccount(
                    entity = row,
                    accountNumber = decryptField(ctx, legacyKey, row.accountNumber),
                    customerId = decryptNullableField(ctx, legacyKey, row.customerId),
                    ifscCode = decryptNullableField(ctx, legacyKey, row.ifscCode),
                    micrCode = decryptNullableField(ctx, legacyKey, row.micrCode),
                    notes = decryptNullableField(ctx, legacyKey, row.notes),
                )
            } catch (_: CorruptedRowException) {
                corruptedKeys += row.key
            }
        }
        return DecryptedTableResult(decrypted, corruptedKeys)
    }

    private suspend fun decryptBankCards(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        rows: List<BankCardDataEntity>,
    ): DecryptedTableResult<DecryptedBankCard> {
        ctx.currentTable = TABLE_BANK_CARD
        val decrypted = ArrayList<DecryptedBankCard>(rows.size)
        val corruptedKeys = mutableListOf<Int>()
        for (row in rows) {
            enterStage(ctx, MigrationStage.DECRYPT_ROW, logBreadcrumb = false)
            try {
                decrypted += DecryptedBankCard(
                    entity = row,
                    name = decryptNullableField(ctx, legacyKey, row.name),
                    number = decryptField(ctx, legacyKey, row.number),
                    pin = decryptNullableField(ctx, legacyKey, row.pin),
                    cvv = decryptNullableField(ctx, legacyKey, row.cvv),
                    expiryDate = decryptNullableField(ctx, legacyKey, row.expiryDate),
                    notes = decryptNullableField(ctx, legacyKey, row.notes),
                )
            } catch (_: CorruptedRowException) {
                corruptedKeys += row.key
            }
        }
        return DecryptedTableResult(decrypted, corruptedKeys)
    }

    private suspend fun decryptSecureNotes(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        rows: List<SecureNoteDataEntity>,
    ): DecryptedTableResult<DecryptedSecureNote> {
        ctx.currentTable = TABLE_SECURE_NOTE
        val decrypted = ArrayList<DecryptedSecureNote>(rows.size)
        val corruptedKeys = mutableListOf<Int>()
        for (row in rows) {
            enterStage(ctx, MigrationStage.DECRYPT_ROW, logBreadcrumb = false)
            try {
                decrypted += DecryptedSecureNote(
                    entity = row,
                    notes = decryptField(ctx, legacyKey, row.notes),
                )
            } catch (_: CorruptedRowException) {
                corruptedKeys += row.key
            }
        }
        return DecryptedTableResult(decrypted, corruptedKeys)
    }

    private suspend fun decryptAuthenticators(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        rows: List<AuthenticatorDataEntity>,
    ): DecryptedTableResult<DecryptedAuthenticator> {
        ctx.currentTable = TABLE_AUTHENTICATOR
        val decrypted = ArrayList<DecryptedAuthenticator>(rows.size)
        val corruptedKeys = mutableListOf<Int>()
        for (row in rows) {
            enterStage(ctx, MigrationStage.DECRYPT_ROW, logBreadcrumb = false)
            try {
                decrypted += DecryptedAuthenticator(
                    entity = row,
                    secretKey = decryptField(ctx, legacyKey, row.secretKey),
                )
            } catch (_: CorruptedRowException) {
                corruptedKeys += row.key
            }
        }
        return DecryptedTableResult(decrypted, corruptedKeys)
    }

    private suspend fun resolvePlaintextHint(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        newHint: String?,
    ): String? {
        ctx.currentTable = TABLE_NONE
        enterStage(ctx, MigrationStage.DECRYPT_HINT)
        if (newHint != null) {
            return newHint
        }
        val storedHint = userDetailsDao.get().getHint()
        if (storedHint.isNullOrBlank()) {
            return null
        }
        return try {
            decryptField(ctx, legacyKey, storedHint)
        } catch (_: CorruptedRowException) {
            // Clear an unreadable legacy hint rather than blocking the vault upgrade.
            ""
        }
    }

    private suspend fun reencryptLogins(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        table: DecryptedTableResult<DecryptedLogin>,
    ) {
        val dao = loginDataDao.get()
        ctx.currentTable = TABLE_LOGIN
        for (key in table.corruptedKeys) {
            dao.deleteLoginDataByKey(key)
            ctx.onRecordProcessed(progress)
        }
        for (row in table.rows) {
            dao.upsertLoginData(
                row.entity.copy(
                    url = encryptAndVerifyNullable(ctx, dekSecretKey, row.url),
                    password = encryptAndVerifyNullable(ctx, dekSecretKey, row.password),
                    notes = encryptAndVerifyNullable(ctx, dekSecretKey, row.notes),
                    userId = encryptAndVerifyField(ctx, dekSecretKey, row.userId),
                ),
            )
            ctx.onRecordProcessed(progress)
        }
    }

    private suspend fun reencryptBankAccounts(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        table: DecryptedTableResult<DecryptedBankAccount>,
    ) {
        val dao = bankAccountDataDao.get()
        ctx.currentTable = TABLE_BANK_ACCOUNT
        for (key in table.corruptedKeys) {
            dao.deleteBankAccountDataByKey(key)
            ctx.onRecordProcessed(progress)
        }
        for (row in table.rows) {
            dao.upsertBankAccountData(
                row.entity.copy(
                    accountNumber = encryptAndVerifyField(ctx, dekSecretKey, row.accountNumber),
                    customerId = encryptAndVerifyNullable(ctx, dekSecretKey, row.customerId),
                    ifscCode = encryptAndVerifyNullable(ctx, dekSecretKey, row.ifscCode),
                    micrCode = encryptAndVerifyNullable(ctx, dekSecretKey, row.micrCode),
                    notes = encryptAndVerifyNullable(ctx, dekSecretKey, row.notes),
                ),
            )
            ctx.onRecordProcessed(progress)
        }
    }

    private suspend fun reencryptBankCards(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        table: DecryptedTableResult<DecryptedBankCard>,
    ) {
        val dao = bankCardDataDao.get()
        ctx.currentTable = TABLE_BANK_CARD
        for (key in table.corruptedKeys) {
            dao.deleteBankCardDataByKey(key)
            ctx.onRecordProcessed(progress)
        }
        for (row in table.rows) {
            dao.upsertBankCardData(
                row.entity.copy(
                    name = encryptAndVerifyNullable(ctx, dekSecretKey, row.name),
                    number = encryptAndVerifyField(ctx, dekSecretKey, row.number),
                    pin = encryptAndVerifyNullable(ctx, dekSecretKey, row.pin),
                    cvv = encryptAndVerifyNullable(ctx, dekSecretKey, row.cvv),
                    expiryDate = encryptAndVerifyNullable(ctx, dekSecretKey, row.expiryDate),
                    notes = encryptAndVerifyNullable(ctx, dekSecretKey, row.notes),
                ),
            )
            ctx.onRecordProcessed(progress)
        }
    }

    private suspend fun reencryptSecureNotes(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        table: DecryptedTableResult<DecryptedSecureNote>,
    ) {
        val dao = secureNoteDataDao.get()
        ctx.currentTable = TABLE_SECURE_NOTE
        for (key in table.corruptedKeys) {
            dao.deleteSecretNoteDataByKey(key)
            ctx.onRecordProcessed(progress)
        }
        for (row in table.rows) {
            dao.upsertSecretNoteData(
                row.entity.copy(
                    notes = encryptAndVerifyField(ctx, dekSecretKey, row.notes),
                ),
            )
            ctx.onRecordProcessed(progress)
        }
    }

    private suspend fun reencryptAuthenticators(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        table: DecryptedTableResult<DecryptedAuthenticator>,
    ) {
        val dao = authenticatorDataDao.get()
        ctx.currentTable = TABLE_AUTHENTICATOR
        for (key in table.corruptedKeys) {
            dao.deleteAuthenticatorDataByKey(key)
            ctx.onRecordProcessed(progress)
        }
        for (row in table.rows) {
            dao.upsertAuthenticatorData(
                row.entity.copy(
                    secretKey = encryptAndVerifyField(ctx, dekSecretKey, row.secretKey),
                ),
            )
            ctx.onRecordProcessed(progress)
        }
    }

    private suspend fun verifyTableCounts(
        ctx: MigrationRunContext,
        loginsBefore: Int,
        loginsDropped: Int,
        accountsBefore: Int,
        accountsDropped: Int,
        cardsBefore: Int,
        cardsDropped: Int,
        notesBefore: Int,
        notesDropped: Int,
        authenticatorsBefore: Int,
        authenticatorsDropped: Int,
    ) {
        enterStage(ctx, MigrationStage.VERIFY_COUNT)
        ctx.currentTable = TABLE_LOGIN
        verifyTableCount(TABLE_LOGIN, loginsBefore, loginsDropped, loginDataDao.get().count())
        ctx.currentTable = TABLE_BANK_ACCOUNT
        verifyTableCount(
            TABLE_BANK_ACCOUNT,
            accountsBefore,
            accountsDropped,
            bankAccountDataDao.get().count(),
        )
        ctx.currentTable = TABLE_BANK_CARD
        verifyTableCount(TABLE_BANK_CARD, cardsBefore, cardsDropped, bankCardDataDao.get().count())
        ctx.currentTable = TABLE_SECURE_NOTE
        verifyTableCount(
            TABLE_SECURE_NOTE,
            notesBefore,
            notesDropped,
            secureNoteDataDao.get().count(),
        )
        ctx.currentTable = TABLE_AUTHENTICATOR
        verifyTableCount(
            TABLE_AUTHENTICATOR,
            authenticatorsBefore,
            authenticatorsDropped,
            authenticatorDataDao.get().count(),
        )
        ctx.currentTable = TABLE_NONE
    }

    private fun verifyPasswordWrapRoundTrip(
        ctx: MigrationRunContext,
        password: CharArray,
        encodedKdfParams: String,
        wrappedPassword: String,
        mintedDek: ByteArray,
        verifier: String,
    ) {
        enterStage(ctx, MigrationStage.VERIFY_WRAP)
        val roundTripDek = envelopeEncryptionManager.unwrapWithPassword(
            password = password.clone(),
            params = KdfParams.decode(encodedKdfParams),
            blob = wrappedPassword,
        ).getOrElse { cause ->
            throw VerifyMismatchException(
                MigrationStage.VERIFY_WRAP,
                "Password wrap round-trip failed to unwrap",
                cause,
            )
        }
        try {
            if (
                !roundTripDek.contentEquals(mintedDek) ||
                !envelopeEncryptionManager.verify(roundTripDek, verifier)
            ) {
                throw VerifyMismatchException(
                    MigrationStage.VERIFY_WRAP,
                    "Password wrap round-trip did not yield the minted DEK",
                )
            }
        } finally {
            roundTripDek.fill(0)
        }
    }

    private suspend fun commitVaultMeta(
        ctx: MigrationRunContext,
        encodedKdfParams: String,
        wrappedPassword: String,
        verifier: String,
        wrappedDevice: String?,
        wrappedBiometric: String?,
    ) {
        enterStage(ctx, MigrationStage.COMMIT_TX)
        vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.ENVELOPE))
        vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.KDF_PARAMS, encodedKdfParams))
        vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_PASSWORD, wrappedPassword))
        vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.DEK_VERIFIER, verifier))
        if (wrappedDevice != null) {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_DEVICE, wrappedDevice))
        }
        if (wrappedBiometric != null) {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.WRAPPED_DEK_BIOMETRIC, wrappedBiometric))
        }
        vaultMetaDao.delete(VaultMetaKey.LEGACY_PASSWORD_HASH)
    }

    private suspend fun decryptNullableField(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        ciphertext: String?,
    ): String? = ciphertext?.let { decryptField(ctx, legacyKey, it) }

    private suspend fun decryptField(
        ctx: MigrationRunContext,
        legacyKey: SecretKey,
        ciphertext: String,
    ): String {
        val structureError = validateCiphertextStructure(ciphertext)
        if (structureError != null) {
            throw CorruptedRowException(structureError)
        }
        return try {
            retryTransientKeystore(ctx) {
                val t0 = System.nanoTime()
                try {
                    AesGcmFieldCipher.decrypt(ciphertext, legacyKey)
                } finally {
                    ctx.legacyDecryptNanos += (System.nanoTime() - t0)
                }
            }
        } catch (e: Exception) {
            when (e) {
                is AEADBadTagException,
                is BadPaddingException,
                is InvalidAlgorithmParameterException,
                is IllegalArgumentException,
                -> throw CorruptedRowException(e)
                else -> throw e
            }
        }
    }

    private fun encryptAndVerifyNullable(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        plaintext: String?,
    ): String? = plaintext?.let { encryptAndVerifyField(ctx, dekSecretKey, it) }

    private fun encryptAndVerifyField(
        ctx: MigrationRunContext,
        dekSecretKey: SecretKeySpec,
        plaintext: String,
    ): String {
        enterStage(ctx, MigrationStage.ENCRYPT_ROW, logBreadcrumb = false)
        val encrypted = AesGcmFieldCipher.encrypt(plaintext, dekSecretKey)

        enterStage(ctx, MigrationStage.VERIFY_ROW, logBreadcrumb = false)
        val decryptedBack = try {
            AesGcmFieldCipher.decrypt(encrypted, dekSecretKey)
        } catch (e: Exception) {
            throw VerifyMismatchException(MigrationStage.VERIFY_ROW, "Verify-after-encrypt threw", e)
        }
        if (decryptedBack != plaintext) {
            throw VerifyMismatchException(
                MigrationStage.VERIFY_ROW,
                "Verify-after-encrypt plaintext mismatch",
            )
        }
        return encrypted
    }

    private fun enterStage(
        ctx: MigrationRunContext,
        stage: MigrationStage,
        logBreadcrumb: Boolean = true,
    ) {
        ctx.currentStage = stage
        if (logBreadcrumb) {
            crashReporter.setKey(KEY_MIGRATION_STAGE, stage.wireName)
            crashReporter.breadcrumb("migration: stage=${stage.wireName}")
        }
        checkDebugFail(stage)
    }

    private fun checkDebugFail(stage: MigrationStage) {
        if (!isDebug || debugFailStage != stage) return
        when (stage) {
            MigrationStage.LOAD_LEGACY_KEY,
            MigrationStage.DECRYPT_ROW,
            MigrationStage.DECRYPT_HINT,
            -> throw KeyStoreException("Debug injected failure at ${stage.wireName}")
            MigrationStage.VERIFY_ROW,
            MigrationStage.VERIFY_COUNT,
            MigrationStage.VERIFY_WRAP,
            -> throw VerifyMismatchException(stage, "Debug injected verify failure at ${stage.wireName}")
            MigrationStage.COMMIT_TX ->
                throw SQLiteException("Debug injected SQLite failure at ${stage.wireName}")
            MigrationStage.DERIVE_KDF,
            MigrationStage.ENCRYPT_ROW,
            -> throw IllegalStateException("Debug injected failure at ${stage.wireName}")
        }
    }

    private fun tryCreateOptionalWrap(
        spec: WrapKeySpec,
        dek: ByteArray,
        aliasSuffix: String?,
    ): String? {
        val wrapName = spec.wireName
        return try {
            val blob = envelopeEncryptionManager.wrapWithKeystoreKey(spec, dek, aliasSuffix)
            analyticsHelper.logEvent(AnalyticsKey.VAULT_WRAP_CREATED) {
                param(AnalyticsParam.WRAP, wrapName)
            }
            blob
        } catch (e: Exception) {
            Timber.w(e, "Failed to create optional %s wrap during migration", wrapName)
            analyticsHelper.logEvent(AnalyticsKey.VAULT_WRAP_CREATE_FAILURE) {
                param(AnalyticsParam.WRAP, wrapName)
                param(AnalyticsParam.REASON, VaultKeyMaintenance.classifyWrapFailureReason(e))
                param(AnalyticsParam.EXCEPTION, e.javaClass.simpleName)
            }
            null
        }
    }

    private suspend fun <T> retryTransientKeystore(
        ctx: MigrationRunContext,
        block: () -> T,
    ): T {
        var lastError: Exception? = null
        for (attemptIndex in 0..RETRY_DELAYS_MS.size) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isTransientKeystoreError(e) || attemptIndex == RETRY_DELAYS_MS.size) {
                    throw e
                }
                lastError = e
                ctx.retriesCount++
                delay(RETRY_DELAYS_MS[attemptIndex])
            }
        }
        throw checkNotNull(lastError)
    }

    private fun verifyTableCount(
        tableName: String,
        rowsBefore: Int,
        dropped: Int,
        rowsAfter: Int,
    ) {
        val expected = rowsBefore - dropped
        if (rowsAfter != expected) {
            throw VerifyMismatchException(
                MigrationStage.VERIFY_COUNT,
                "Row count mismatch on $tableName: expected $expected, got $rowsAfter",
            )
        }
    }

    private fun classifyException(
        currentStage: MigrationStage,
        error: Exception,
    ): Pair<MigrationStage, MigrationFailureReason> = when {
        isTransientKeystoreError(error) -> currentStage to MigrationFailureReason.KEYSTORE_TRANSIENT
        error is SQLiteException || error is IOException -> {
            val stage = if (currentStage == MigrationStage.COMMIT_TX) currentStage else MigrationStage.COMMIT_TX
            stage to MigrationFailureReason.DB_IO
        }
        else -> currentStage to MigrationFailureReason.UNEXPECTED
    }

    private fun recordFailure(
        ctx: MigrationRunContext,
        stage: MigrationStage,
        reason: MigrationFailureReason,
        cause: Throwable?,
    ): MigrationOutcome.Failure {
        val durationMs = (System.currentTimeMillis() - ctx.startTimeMs).coerceAtLeast(0L)
        val exceptionName = cause?.javaClass?.simpleName ?: EXCEPTION_NONE
        analyticsHelper.logEvent(AnalyticsKey.VAULT_MIGRATION_FAILURE) {
            param(AnalyticsParam.STAGE, stage.wireName)
            param(AnalyticsParam.REASON, reason.wireName)
            param(AnalyticsParam.TABLE, ctx.currentTable)
            param(AnalyticsParam.EXCEPTION, exceptionName)
            param(AnalyticsParam.RECORDS_DONE, ctx.recordsDone)
            param(AnalyticsParam.RECORD_COUNT, ctx.totalRecords)
            param(AnalyticsParam.RETRIES, ctx.retriesCount)
            param(AnalyticsParam.DURATION_MS, durationMs)
            param(AnalyticsParam.ATTEMPT, ctx.attempt)
        }
        crashReporter.setKey(KEY_MIGRATION_STAGE, stage.wireName)
        crashReporter.setKey(KEY_MIGRATION_REASON, reason.wireName)
        crashReporter.setKey(KEY_MIGRATION_TABLE, ctx.currentTable)
        crashReporter.setKey(KEY_MIGRATION_RECORDS_DONE, ctx.recordsDone)
        crashReporter.setKey(KEY_MIGRATION_RECORD_COUNT, ctx.totalRecords)
        crashReporter.setKey(KEY_MIGRATION_ATTEMPT, ctx.attempt)
        crashReporter.recordNonFatal(
            VaultMigrationException(
                stage = stage,
                reason = reason,
                table = ctx.currentTable,
                recordsDone = ctx.recordsDone,
                totalRecords = ctx.totalRecords,
                cause = cause,
            ),
        )
        return MigrationOutcome.Failure(
            stage = stage,
            reason = reason,
            attempt = ctx.attempt,
            trigger = ctx.trigger,
            cause = cause,
        )
    }

    private class MigrationRunContext(
        val startTimeMs: Long,
        val totalRecords: Int,
        val attempt: Int,
        val trigger: MigrationTrigger,
        var currentStage: MigrationStage = MigrationStage.LOAD_LEGACY_KEY,
        var currentTable: String = TABLE_NONE,
        var recordsDone: Int = 0,
        var retriesCount: Int = 0,
        var legacyDecryptNanos: Long = 0L,
    ) {
        fun onRecordProcessed(progress: VaultMigrationProgress) {
            recordsDone++
            progress.onRecordProcessed(recordsDone, totalRecords)
        }
    }

    companion object {
        const val TABLE_LOGIN = "login"
        const val TABLE_BANK_ACCOUNT = "bank_account"
        const val TABLE_BANK_CARD = "bank_card"
        const val TABLE_SECURE_NOTE = "secure_note"
        const val TABLE_AUTHENTICATOR = "authenticator"
        const val TABLE_NONE = "none"

        const val KEY_CRYPTO_VERSION = "crypto_version"
        const val KEY_MIGRATION_STAGE = "migration_stage"
        const val KEY_MIGRATION_REASON = "migration_reason"
        const val KEY_MIGRATION_TABLE = "migration_table"
        const val KEY_MIGRATION_RECORDS_DONE = "migration_records_done"
        const val KEY_MIGRATION_RECORD_COUNT = "migration_record_count"
        const val KEY_MIGRATION_ATTEMPT = "migration_attempt"

        private const val EXCEPTION_NONE = "none"
        private const val AES_ALGORITHM = "AES"
        private const val BIOMETRIC_DISABLED_VALUE = "1"
        private const val GCM_TAG_SIZE_BYTES = 16
        private const val NANOS_PER_MILLI = 1_000_000L

        private val RETRY_DELAYS_MS = longArrayOf(100L, 300L, 900L)

        /**
         * Validates that [data] has the `b64(ciphertext)|b64(iv)` structure with a non-empty IV and
         * at least a 16-byte GCM authentication tag before passing it to Keystore.
         */
        internal fun validateCiphertextStructure(data: String): IllegalArgumentException? {
            val parts = data.split(AesGcmFieldCipher.SEPARATOR, limit = 2)
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return IllegalArgumentException("Missing IV separator in field ciphertext")
            }
            return try {
                val ciphertextBytes = SecurityUtils.decodeBase64(parts[0])
                val ivBytes = SecurityUtils.decodeBase64(parts[1])
                if (ivBytes.isEmpty() || ciphertextBytes.size < GCM_TAG_SIZE_BYTES) {
                    IllegalArgumentException("Decoded ciphertext or IV shorter than GCM minimum")
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                e
            }
        }

        /**
         * Returns `true` when [error] is a transient Android Keystore failure eligible for retry
         * (`KeyStoreException`, `ProviderException`, `ServiceSpecificException`, or an
         * `IllegalBlockSizeException` not caused by `AEADBadTagException`).
         */
        internal fun isTransientKeystoreError(error: Throwable): Boolean {
            if (error is AEADBadTagException || error.cause is AEADBadTagException) {
                return false
            }
            return error is KeyStoreException ||
                error is ProviderException ||
                error is IllegalBlockSizeException ||
                error.javaClass.simpleName == "ServiceSpecificException" ||
                error.cause?.let { isTransientKeystoreError(it) } == true
        }

        internal fun buildWrapsParam(
            hasDeviceWrap: Boolean,
            hasBiometricWrap: Boolean,
        ): String = when {
            hasDeviceWrap && hasBiometricWrap -> "device_biometric"
            hasDeviceWrap -> "device"
            hasBiometricWrap -> "biometric"
            else -> "none"
        }
    }
}
