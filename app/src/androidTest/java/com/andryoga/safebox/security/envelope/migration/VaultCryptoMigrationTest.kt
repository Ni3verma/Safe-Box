package com.andryoga.safebox.security.envelope.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.e2e.E2ETestUtils
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.envelope.AndroidKeystoreGateway
import com.andryoga.safebox.security.envelope.interfaces.SessionKeyHolder
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.test.fixtures.LegacyVaultFixtures
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * [LegacyVaultMigrator] against a real Room database and the real legacy `symmetricDataKey` in
 * Android Keystore: the unit suite proves the decisions with fakes, this proves the bytes.
 *
 * The test gateway routes only the legacy key to the real Keystore ([FakeKeystoreGateway.legacyKeyDelegate]);
 * the key is not auth-bound, so the class runs on the CI image. It deletes this `applicationId`'s
 * legacy alias before and after, like `AndroidKeystoreGatewayLegacyKeyTest`.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class VaultCryptoMigrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var migrator: LegacyVaultMigrator

    @Inject
    lateinit var database: SafeBoxDatabase

    @Inject
    lateinit var keystoreGateway: FakeKeystoreGateway

    @Inject
    lateinit var sessionKeyHolder: SessionKeyHolder

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

    private val realGateway = AndroidKeystoreGateway()
    private lateinit var legacyKey: SecretKey

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
        realGateway.deleteLegacyVaultKey()
        legacyKey = LegacyVaultFixtures.seedLegacyKeystoreKey()
        keystoreGateway.legacyKeyDelegate = realGateway
        runBlocking {
            LegacyVaultFixtures.seedLegacyVault(database, legacyKey, PASSWORD, HINT)
            withLegacySession {
                E2ETestUtils.setupSeededVaultRecords(
                    database,
                    loginDataRepository,
                    bankCardDataRepository,
                    bankAccountDataRepository,
                    secureNoteDataRepository,
                    authenticatorDataRepository,
                )
            }
        }
    }

    @After
    fun tearDown() {
        migrator.debugFailStage = null
        sessionKeyHolder.clearSessionKey()
        database.clearAllTables()
        realGateway.deleteLegacyVaultKey()
    }

    @Test
    fun migrate_withARealLegacyKeystoreKey_shouldReencryptEveryFieldUnderTheDekAndDropLegacyMaterial() = runBlocking<Unit> {
        val legacyCiphertext = checkNotNull(database.loginDataDao().getLoginDataByKey(LOGIN_KEY).password)

        val outcome = migrator.migrate(PASSWORD.toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        val dek = SecretKeySpec((outcome as MigrationOutcome.Success).dek, "AES")
        val newCiphertext = checkNotNull(database.loginDataDao().getLoginDataByKey(LOGIN_KEY).password)
        assertThat(newCiphertext).isNotEqualTo(legacyCiphertext)
        assertThat(AesGcmFieldCipher.decrypt(newCiphertext, dek)).isEqualTo(LOGIN_PASSWORD)
        // The migrator opened the DEK session, so every record type reads back through its repository.
        assertThat(loginDataRepository.getLoginDataByKey(LOGIN_KEY).password).isEqualTo(LOGIN_PASSWORD)
        assertThat(bankCardDataRepository.getBankCardDataByKey(902).number).isEqualTo("4111222233334444")
        assertThat(bankAccountDataRepository.getBankAccountDataByKey(903).accountNo).isEqualTo("9876543210")
        assertThat(secureNoteDataRepository.getSecureNoteDataByKey(904).notes).contains("SecureWifiPassword#2026")
        assertThat(authenticatorDataRepository.getAuthenticatorDataByKey(905).config.secretKey)
            .isEqualTo(E2ETestUtils.TEST_TOTP_SECRET_KEY)
        assertThat(database.userDetailsDao().getHint()).isEqualTo(HINT)
        assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.ENVELOPE)
        assertThat(database.vaultMetaDao().get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isNull()
        assertThat(realGateway.legacyVaultKey()).isNull()
    }

    @Test
    fun migrate_whenTheCommitFails_shouldRollBackToAReadableLegacyVaultThatMigratesOnRetry() = runBlocking<Unit> {
        val before = database.loginDataDao().getLoginDataByKey(LOGIN_KEY)
        migrator.debugFailStage = MigrationStage.COMMIT_TX

        val failure = migrator.migrate(PASSWORD.toCharArray())

        assertThat(failure).isInstanceOf(MigrationOutcome.Failure::class.java)
        assertThat((failure as MigrationOutcome.Failure).stage).isEqualTo(MigrationStage.COMMIT_TX)
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.LEGACY)
        assertThat(database.vaultMetaDao().get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isNotNull()
        assertThat(database.vaultMetaDao().get(VaultMetaKey.WRAPPED_DEK_PASSWORD)).isNull()
        assertThat(database.loginDataDao().getLoginDataByKey(LOGIN_KEY)).isEqualTo(before)
        val stillLegacyKey = checkNotNull(realGateway.legacyVaultKey())
        assertThat(AesGcmFieldCipher.decrypt(checkNotNull(before.password), stillLegacyKey)).isEqualTo(LOGIN_PASSWORD)

        migrator.debugFailStage = null
        val retry = migrator.migrate(PASSWORD.toCharArray())

        assertThat(retry).isInstanceOf(MigrationOutcome.Success::class.java)
        assertThat(loginDataRepository.getLoginDataByKey(LOGIN_KEY).password).isEqualTo(LOGIN_PASSWORD)
    }

    @Test
    fun migrate_whenARowIsCorruptedOnDisk_shouldDropOnlyThatRowAndMigrateTheRest() = runBlocking<Unit> {
        withLegacySession {
            loginDataRepository.upsertLoginData(
                LoginData(
                    id = CORRUPTED_KEY,
                    title = "Damaged Login",
                    url = null,
                    userId = "damaged@example.com",
                    password = "DamagedPassword1!",
                    notes = null,
                    creationDate = E2ETestUtils.TEST_DATE,
                    updateDate = E2ETestUtils.TEST_DATE,
                ),
            )
        }
        // Well-formed (24-byte body, 12-byte IV) but the GCM tag fails under the real legacy key.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE login_data SET password = 'Zm9vYmFyYmF6cXV4Zm9vYmFyYmF6cXV4|YmFyYmF6cXV4Zm9v' WHERE `key` = $CORRUPTED_KEY",
        )

        val outcome = migrator.migrate(PASSWORD.toCharArray())

        assertThat(outcome).isInstanceOf(MigrationOutcome.Success::class.java)
        assertThat(database.loginDataDao().count()).isEqualTo(1)
        assertThat(loginDataRepository.getLoginDataByKey(LOGIN_KEY).password).isEqualTo(LOGIN_PASSWORD)
        assertThat(bankCardDataRepository.getBankCardDataByKey(902).number).isEqualTo("4111222233334444")
        assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.ENVELOPE)
    }

    /** Writes through the repositories under the legacy key, as a pre-envelope build did. */
    private suspend fun withLegacySession(block: suspend () -> Unit) {
        sessionKeyHolder.setLegacySession(legacyKey)
        try {
            block()
        } finally {
            sessionKeyHolder.clearSessionKey()
        }
    }

    private companion object {
        const val PASSWORD = "Legacy#Pass12"
        const val HINT = "legacy hint"
        const val LOGIN_KEY = 901
        const val LOGIN_PASSWORD = "ApplePassword123!"
        const val CORRUPTED_KEY = 906
    }
}
