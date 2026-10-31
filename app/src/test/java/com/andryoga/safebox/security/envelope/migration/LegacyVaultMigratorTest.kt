@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.security.envelope.migration

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
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
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.Base64Rule
import com.andryoga.safebox.security.envelope.EnvelopeEncryptionManagerImpl
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakeCrashReporter
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.ProviderException
import java.util.Date
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for [LegacyVaultMigrator] covering the happy-path re-encryption across all 5 record
 * tables + hint, `FORGOT_PASSWORD` trigger, missing legacy key, transient Keystore retries and
 * exhaustion, dropping permanently undecryptable rows, verify-after-encrypt / row-count /
 * password-wrap verification, SQLite commit failure, debug fault injection, and analytics +
 * Crashlytics reporting.
 */
class LegacyVaultMigratorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val base64Rule = Base64Rule()

    @MockK
    lateinit var database: SafeBoxDatabase

    @MockK(relaxUnitFun = true)
    lateinit var loginDataDao: LoginDataDao

    @MockK(relaxUnitFun = true)
    lateinit var bankAccountDataDao: BankAccountDataDao

    @MockK(relaxUnitFun = true)
    lateinit var bankCardDataDao: BankCardDataDao

    @MockK(relaxUnitFun = true)
    lateinit var secureNoteDataDao: SecureNoteDataDao

    @MockK(relaxUnitFun = true)
    lateinit var authenticatorDataDao: AuthenticatorDataDao

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDao: UserDetailsDao

    @MockK(relaxUnitFun = true)
    lateinit var vaultMetaDao: VaultMetaDao

    @MockK
    lateinit var deviceSecurityStatus: DeviceSecurityStatus

    @RelaxedMockK
    lateinit var workManager: WorkManager

    private lateinit var fakeKeystoreGateway: FakeKeystoreGateway
    private lateinit var envelopeEncryptionManager: EnvelopeEncryptionManager
    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var progress: VaultMigrationProgress
    private lateinit var fakeAnalytics: FakeAnalyticsHelper
    private lateinit var fakeCrashReporter: FakeCrashReporter
    private lateinit var migrator: LegacyVaultMigrator
    private lateinit var legacyKey: SecretKey

    private val now = Date(1_700_000_000_000L)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        fakeKeystoreGateway = spyk(FakeKeystoreGateway())
        legacyKey = fakeKeystoreGateway.getOrCreateLegacyVaultKey()
        envelopeEncryptionManager = spyk(EnvelopeEncryptionManagerImpl(fakeKeystoreGateway))
        sessionKeyHolder = SessionKeyHolderImpl()
        progress = VaultMigrationProgress()
        fakeAnalytics = FakeAnalyticsHelper()
        fakeCrashReporter = FakeCrashReporter()

        mockkStatic(ROOM_DATABASE_EXTENSIONS)
        val txSlot = slot<suspend () -> Any?>()
        coEvery { database.withTransaction(capture(txSlot)) } coAnswers {
            txSlot.captured.invoke()
        }

        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.LEGACY
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.DEVICE_KEY_ALIAS_SUFFIX) } returns null
        coEvery { vaultMetaDao.get(VaultMetaKey.BIOMETRIC_KEY_ALIAS_SUFFIX) } returns null
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = BiometricClass.STRONG,
            hasStrongBox = false,
        )

        coEvery { loginDataDao.getAllEntities() } returns emptyList()
        coEvery { bankAccountDataDao.getAllEntities() } returns emptyList()
        coEvery { bankCardDataDao.getAllEntities() } returns emptyList()
        coEvery { secureNoteDataDao.getAllEntities() } returns emptyList()
        coEvery { authenticatorDataDao.getAllEntities() } returns emptyList()
        coEvery { loginDataDao.count() } returns 0
        coEvery { bankAccountDataDao.count() } returns 0
        coEvery { bankCardDataDao.count() } returns 0
        coEvery { secureNoteDataDao.count() } returns 0
        coEvery { authenticatorDataDao.count() } returns 0
        coEvery { userDetailsDao.getHint() } returns null

        migrator = LegacyVaultMigrator(
            database = Lazy { database },
            loginDataDao = Lazy { loginDataDao },
            bankAccountDataDao = Lazy { bankAccountDataDao },
            bankCardDataDao = Lazy { bankCardDataDao },
            secureNoteDataDao = Lazy { secureNoteDataDao },
            authenticatorDataDao = Lazy { authenticatorDataDao },
            userDetailsDao = Lazy { userDetailsDao },
            vaultMetaDao = vaultMetaDao,
            keystoreGateway = fakeKeystoreGateway,
            envelopeEncryptionManager = envelopeEncryptionManager,
            sessionKeyHolder = sessionKeyHolder,
            deviceSecurityStatus = deviceSecurityStatus,
            workManager = Lazy { workManager },
            progress = progress,
            analyticsHelper = fakeAnalytics,
            crashReporter = fakeCrashReporter,
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
            applicationScope = CoroutineScope(mainDispatcherRule.testDispatcher),
            isDebug = true,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_EXTENSIONS)
    }

    @Test
    fun migrate_whenVaultAlreadyAtEnvelopeVersion_shouldReturnAlreadyMigratedAndLogNothing() = runTest {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns CryptoVersion.ENVELOPE
        val password = "MasterPassword#1".toCharArray()

        val outcome = migrator.migrate(password)

        assertThat(outcome).isEqualTo(MigrationOutcome.AlreadyMigrated)
        assertThat(password.all { it == '\u0000' }).isTrue()
        assertThat(fakeAnalytics.loggedEvents).isEmpty()
    }

    @Test
    fun migrate_happyPath_shouldReencryptAllTablesAndHintCommitMetaOpenDekSessionAndDeleteLegacyKey() = runTest {
        seedOneRecordPerTable()
        coEvery { userDetailsDao.getHint() } returns encryptLegacy("legacy master hint")
        val updatedLoginSlot = slot<LoginDataEntity>()
        coEvery { loginDataDao.upsertLoginData(capture(updatedLoginSlot)) } returns Unit
        val updatedHintSlot = slot<String>()
        coEvery { userDetailsDao.updateHint(capture(updatedHintSlot), any()) } returns Unit

        val outcome = migrator.migrate("MasterPassword#1".toCharArray(), trigger = MigrationTrigger.LOGIN)

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        val success = outcome as MigrationOutcome.Success

        val dekSecret = SecretKeySpec(success.dek, "AES")
        assertThat(AesGcmFieldCipher.decrypt(updatedLoginSlot.captured.userId, dekSecret))
            .isEqualTo("octocat")
        assertThat(AesGcmFieldCipher.decrypt(updatedLoginSlot.captured.password!!, dekSecret))
            .isEqualTo("gh-secret")
        assertThat(updatedHintSlot.captured).isEqualTo("legacy master hint")

        coVerify {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.ENVELOPE))
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.KDF_PARAMS })
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.WRAPPED_DEK_PASSWORD })
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.DEK_VERIFIER })
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.WRAPPED_DEK_DEVICE })
            vaultMetaDao.upsert(match { it.key == VaultMetaKey.WRAPPED_DEK_BIOMETRIC })
            vaultMetaDao.delete(VaultMetaKey.LEGACY_PASSWORD_HASH)
        }
        assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
        assertThat(sessionKeyHolder.isLegacySession).isFalse()
        assertThat(fakeKeystoreGateway.legacyVaultKey()).isNull()
        assertThat(progress.state.value).isEqualTo(MigrationProgressState.Idle)

        val startEvent = fakeAnalytics.loggedEvents.first {
            it.key == AnalyticsKey.VAULT_MIGRATION_START
        }
        assertThat(startEvent.params[AnalyticsParam.TRIGGER.paramName]).isEqualTo("login")
        val successEvent = fakeAnalytics.loggedEvents.first {
            it.key == AnalyticsKey.VAULT_MIGRATION_SUCCESS
        }
        assertThat(successEvent.params[AnalyticsParam.ATTEMPT.paramName]).isEqualTo(1)
        assertThat(successEvent.params[AnalyticsParam.RECORD_COUNT.paramName]).isEqualTo(5)
        assertThat(successEvent.params[AnalyticsParam.WRAPS.paramName])
            .isEqualTo("device_biometric")
    }

    @Test
    fun migrate_withForgotPasswordTrigger_shouldWriteNewHintWithoutDecryptingOldHint() = runTest {
        coEvery { userDetailsDao.getHint() } returns "corrupted-legacy-hint"
        val updatedHintSlot = slot<String>()
        coEvery { userDetailsDao.updateHint(capture(updatedHintSlot), any()) } returns Unit

        val outcome = migrator.migrate(
            password = "NewResetPassword#1".toCharArray(),
            newHint = "fresh hint",
            trigger = MigrationTrigger.FORGOT_PASSWORD,
        )

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        assertThat(updatedHintSlot.captured).isEqualTo("fresh hint")
        val startEvent = fakeAnalytics.loggedEvents.first {
            it.key == AnalyticsKey.VAULT_MIGRATION_START
        }
        assertThat(startEvent.params[AnalyticsParam.TRIGGER.paramName]).isEqualTo("forgot_password")
    }

    @Test
    fun migrate_whenLegacyKeyIsMissing_shouldReturnLegacyKeyLost() = runTest {
        fakeKeystoreGateway.deleteLegacyVaultKey()

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.LOAD_LEGACY_KEY)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.LEGACY_KEY_LOST)
        val failureEvent = fakeAnalytics.loggedEvents.first {
            it.key == AnalyticsKey.VAULT_MIGRATION_FAILURE
        }
        assertThat(failureEvent.params[AnalyticsParam.REASON.paramName]).isEqualTo("legacy_key_lost")
    }

    @Test
    fun migrate_whenTransientKeystoreErrorRecoversWithinRetries_shouldSucceedAndReportRetryCount() = runTest {
        var remainingTransientFailures = 2
        val flakyLegacyKey = object : SecretKey by legacyKey {
            override fun getEncoded(): ByteArray {
                if (remainingTransientFailures > 0) {
                    remainingTransientFailures--
                    throw ProviderException("Transient Keystore binder error")
                }
                return legacyKey.encoded
            }
        }
        every { fakeKeystoreGateway.legacyVaultKey() } returns flakyLegacyKey
        coEvery { loginDataDao.getAllEntities() } returns listOf(
            LoginDataEntity(
                key = 1,
                title = "Title",
                url = null,
                password = encryptLegacy("Pass"),
                notes = null,
                userId = encryptLegacy("User"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { loginDataDao.count() } returns 1

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        val successEvent = fakeAnalytics.loggedEvents.first {
            it.key == AnalyticsKey.VAULT_MIGRATION_SUCCESS
        }
        assertThat(successEvent.params[AnalyticsParam.RETRIES.paramName]).isEqualTo(2)
    }

    @Test
    fun migrate_whenTransientKeystoreErrorExhaustsRetries_shouldReturnKeystoreTransientWithoutTransaction() = runTest {
        val alwaysFailingKey = object : SecretKey by legacyKey {
            override fun getEncoded(): ByteArray {
                throw ProviderException("Persistent Keystore binder timeout")
            }
        }
        every { fakeKeystoreGateway.legacyVaultKey() } returns alwaysFailingKey
        coEvery { loginDataDao.getAllEntities() } returns listOf(
            LoginDataEntity(
                key = 1,
                title = "Title",
                url = null,
                password = encryptLegacy("Pass"),
                notes = null,
                userId = encryptLegacy("User"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { loginDataDao.count() } returns 1

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.DECRYPT_ROW)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.KEYSTORE_TRANSIENT)
        coVerify(exactly = 0) { database.withTransaction(any<suspend () -> Any?>()) }
    }

    @Test
    fun migrate_whenRowsFailGcmTagOrFormat_shouldDeleteCorruptedRowsDeleteLegacyKeyAndFinishUpgrade() = runTest {
        val foreignKey = SecretKeySpec(ByteArray(32) { 0x55 }, "AES")
        val healthyLogin = LoginDataEntity(
            key = 10,
            title = "Healthy Login",
            url = null,
            password = encryptLegacy("Secret"),
            notes = null,
            userId = encryptLegacy("user@example.com"),
            creationDate = now,
            updateDate = now,
        )
        val badTagLogin = LoginDataEntity(
            key = 11,
            title = "Wrong Key Login",
            url = null,
            password = AesGcmFieldCipher.encrypt("Wrong Key Secret", foreignKey),
            notes = null,
            userId = encryptLegacy("user2@example.com"),
            creationDate = now,
            updateDate = now,
        )
        val malformedNote = SecureNoteDataEntity(
            key = 22,
            title = "Malformed Note",
            notes = "not-base64-and-no-iv-separator",
            creationDate = now,
            updateDate = now,
        )
        coEvery { loginDataDao.getAllEntities() } returns listOf(healthyLogin, badTagLogin)
        coEvery { secureNoteDataDao.getAllEntities() } returns listOf(malformedNote)
        // After deleting 1 corrupted login and 1 corrupted note, counts are 1 and 0.
        coEvery { loginDataDao.count() } returns 1
        coEvery { secureNoteDataDao.count() } returns 0

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        coVerify(exactly = 1) { loginDataDao.deleteLoginDataByKey(11) }
        coVerify(exactly = 1) { secureNoteDataDao.deleteSecretNoteDataByKey(22) }
        assertThat(fakeKeystoreGateway.legacyVaultKey()).isNull()
    }

    @Test
    fun migrate_whenPostMigrationRowCountMismatchesExpected_shouldAbortWithVerifyCount() = runTest {
        coEvery { loginDataDao.getAllEntities() } returns listOf(
            LoginDataEntity(
                key = 1,
                title = "Login",
                url = null,
                password = encryptLegacy("Pass"),
                notes = null,
                userId = encryptLegacy("id"),
                creationDate = now,
                updateDate = now,
            ),
        )
        // Expected 1 surviving row, but DAO reports 0.
        coEvery { loginDataDao.count() } returns 0

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.VERIFY_COUNT)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.VERIFY_MISMATCH)
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun migrate_whenPasswordWrapRoundTripFailsBeforeCommit_shouldAbortWithVerifyWrap() = runTest {
        every {
            envelopeEncryptionManager.unwrapWithPassword(any(), any(), any())
        } returns Result.success(ByteArray(32) { 0x7F })

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.VERIFY_WRAP)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.VERIFY_MISMATCH)
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun migrate_whenSqliteExceptionOccursDuringCommit_shouldReturnDbIo() = runTest {
        coEvery {
            vaultMetaDao.upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.ENVELOPE))
        } throws SQLiteException("database or disk is full")

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.COMMIT_TX)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.DB_IO)
        assertThat(fakeCrashReporter.nonFatals).hasSize(1)
        assertThat(fakeCrashReporter.nonFatals.first()).isInstanceOf(VaultMigrationException::class.java)
    }

    @Test
    fun migrate_whenDebugFailStageIsConfigured_shouldFailAtThatStageWithExpectedReason() = runTest {
        seedOneRecordPerTable()
        migrator.debugFailStage = MigrationStage.VERIFY_ROW

        val outcome = migrator.migrate("MasterPassword#1".toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Failure::class.java)
        val failure = outcome as MigrationOutcome.Failure
        assertThat(failure.stage).isEqualTo(MigrationStage.VERIFY_ROW)
        assertThat(failure.reason).isEqualTo(MigrationFailureReason.VERIFY_MISMATCH)
    }

    private fun encryptLegacy(plaintext: String): String =
        AesGcmFieldCipher.encrypt(plaintext, legacyKey)

    private fun seedOneRecordPerTable() {
        coEvery { loginDataDao.getAllEntities() } returns listOf(
            LoginDataEntity(
                key = 1,
                title = "GitHub",
                url = encryptLegacy("github.com"),
                password = encryptLegacy("gh-secret"),
                notes = encryptLegacy("work account"),
                userId = encryptLegacy("octocat"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { bankAccountDataDao.getAllEntities() } returns listOf(
            BankAccountDataEntity(
                key = 2,
                title = "Checking",
                accountNumber = encryptLegacy("1234567890"),
                customerName = "Ada",
                customerId = encryptLegacy("C-1"),
                branchCode = "B-1",
                branchName = "Main",
                branchAddress = "1 High St",
                ifscCode = encryptLegacy("IFSC0001"),
                micrCode = encryptLegacy("123456"),
                notes = encryptLegacy("Primary"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { bankCardDataDao.getAllEntities() } returns listOf(
            BankCardDataEntity(
                key = 3,
                title = "Visa",
                name = encryptLegacy("Ada Lovelace"),
                number = encryptLegacy("4111222233334444"),
                pin = encryptLegacy("1234"),
                cvv = encryptLegacy("999"),
                expiryDate = encryptLegacy("12/29"),
                notes = encryptLegacy("Travel"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { secureNoteDataDao.getAllEntities() } returns listOf(
            SecureNoteDataEntity(
                key = 4,
                title = "Wi-Fi",
                notes = encryptLegacy("correct-horse-battery-staple"),
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { authenticatorDataDao.getAllEntities() } returns listOf(
            AuthenticatorDataEntity(
                key = 5,
                title = "GitHub 2FA",
                secretKey = encryptLegacy("JBSWY3DPEHPK3PXP"),
                algorithm = TotpAlgorithm.SHA1,
                digits = 6,
                period = 30,
                creationDate = now,
                updateDate = now,
            ),
        )
        coEvery { loginDataDao.count() } returns 1
        coEvery { bankAccountDataDao.count() } returns 1
        coEvery { bankCardDataDao.count() } returns 1
        coEvery { secureNoteDataDao.count() } returns 1
        coEvery { authenticatorDataDao.count() } returns 1
    }

    private companion object {
        const val ROOM_DATABASE_EXTENSIONS = "androidx.room.RoomDatabaseKt"
    }
}
