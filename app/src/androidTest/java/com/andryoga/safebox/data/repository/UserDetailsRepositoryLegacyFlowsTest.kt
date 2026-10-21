package com.andryoga.safebox.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
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
 * who signs up on this build, or who upgraded and never ran the crypto migration, relies on.
 *
 * The legacy key comes from the fake gateway bound in instrumentation ([FakeKeystoreGateway]);
 * `setUp` creates it the way the signup bridge does, and the lost-key cases take it away again.
 */
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
        // What SignupViewModel does before writing the user row: the hint is encrypted under it.
        keystoreGateway.getOrCreateLegacyVaultKey()
    }

    @Test
    fun signup_shouldCreateLegacyVaultThatAcceptsOnlyItsPassword() = runBlocking {
        repository.insertUserDetailsData(password = "Qwerty@@123", hint = "first hint")

        assertThat(database.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION))
            .isEqualTo(CryptoVersion.LEGACY)
        assertThat(repository.checkPassword("Qwerty@@123")).isTrue()
        assertThat(repository.checkPassword("Qwerty@@124")).isFalse()
        assertThat(repository.getHint()).isEqualTo("first hint")
        // A legacy vault keeps the hint as ciphertext on disk.
        assertThat(database.userDetailsDao().getHint()).isNotEqualTo("first hint")
    }

    @Test
    fun updatePasswordAndHint_shouldReplaceCredentialAndHintForTheSameUser() = runBlocking {
        repository.insertUserDetailsData(password = "Qwerty@@123", hint = "first hint")
        val uid = database.userDetailsDao().getUid()

        repository.updatePasswordAndHint(newPassword = "Asdfgh@@456", hint = "second hint")

        assertThat(repository.checkPassword("Asdfgh@@456")).isTrue()
        assertThat(repository.checkPassword("Qwerty@@123")).isFalse()
        assertThat(repository.getHint()).isEqualTo("second hint")
        assertThat(database.userDetailsDao().getUid()).isEqualTo(uid)
    }

    @Test
    fun getHint_whenTheLegacyKeyIsLost_shouldReturnNullAndLeaveTheCredentialIntact() = runBlocking {
        repository.insertUserDetailsData(password = "Qwerty@@123", hint = "first hint")

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
        repository.insertUserDetailsData(password = "Qwerty@@123", hint = "first hint")
        keystoreGateway.deleteLegacyVaultKey()
        // What the pre-MR 3 provider used to do silently on a lost alias.
        keystoreGateway.getOrCreateLegacyVaultKey()

        val result = runCatching { repository.getHint() }

        assertThat(result.isFailure).isTrue()
    }
}
