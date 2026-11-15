package com.andryoga.safebox.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.test.fixtures.LegacyVaultFixtures
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The legacy credential flows against the real repository, Room and hint encryption: what a user
 * who upgraded from `crypto_version = 1` and has not yet run the crypto migration relies on.
 *
 * The legacy key comes from the fake gateway bound in instrumentation ([FakeKeystoreGateway]);
 * `setUp` seeds it and the lost-key cases take it away again.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserDetailsRepositoryLegacyFlowsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: UserDetailsRepository

    @Inject
    lateinit var database: SafeBoxDatabase

    @Inject
    lateinit var keystoreGateway: FakeKeystoreGateway

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
        keystoreGateway.getOrCreateLegacyVaultKey()
    }

    @Test
    fun legacyVault_shouldAcceptOnlyItsPasswordAndEncryptHint() = runBlocking {
        seedLegacyVault(password = "Qwerty@@123", hint = "first hint")

        assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION))
            .isEqualTo(CryptoVersion.LEGACY)
        assertThat(repository.checkPassword("Qwerty@@123")).isTrue()
        assertThat(repository.checkPassword("Qwerty@@124")).isFalse()
        assertThat(repository.getHint()).isEqualTo("first hint")
        // A legacy vault keeps the hint as ciphertext on disk.
        assertThat(database.userDetailsDao().getHint()).isNotEqualTo("first hint")
    }

    @Test
    fun getHint_whenTheLegacyKeyIsLost_shouldReturnNullAndLeaveTheCredentialIntact() = runBlocking {
        seedLegacyVault(password = "Qwerty@@123", hint = "first hint")

        keystoreGateway.deleteLegacyVaultKey()

        // The hint cannot be read, nothing regenerates the key, and the password check (which
        // never needed the key) still answers so the login screen can report the lost key.
        assertThat(repository.getHint()).isNull()
        assertThat(keystoreGateway.legacyVaultKey()).isNull()
        assertThat(repository.checkPassword("Qwerty@@123")).isTrue()
        assertThat(database.userDetailsDao().getHint()).isNotEqualTo("first hint")
    }

    @Test
    fun getHint_afterTheKeyWasLostAndReplaced_shouldFailInsteadOfReadingGarbage() = runBlocking {
        seedLegacyVault(password = "Qwerty@@123", hint = "first hint")
        keystoreGateway.deleteLegacyVaultKey()
        // What the old ambient-key provider did silently on a lost alias.
        keystoreGateway.getOrCreateLegacyVaultKey()

        val result = runCatching { repository.getHint() }

        assertThat(result.isFailure).isTrue()
    }

    /** A vault as a pre-envelope build left it: hash parked by `MIGRATION_5_6`, hint under the legacy key. */
    private suspend fun seedLegacyVault(password: String, hint: String) {
        LegacyVaultFixtures.seedLegacyVault(
            database = database,
            legacyKey = checkNotNull(keystoreGateway.legacyVaultKey()),
            password = password,
            hint = hint,
        )
    }
}
