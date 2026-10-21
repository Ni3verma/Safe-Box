package com.andryoga.safebox.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
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

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
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
}
