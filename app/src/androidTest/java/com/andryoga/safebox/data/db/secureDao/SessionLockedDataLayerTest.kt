package com.andryoga.safebox.data.db.secureDao

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.totp.models.TotpConfig
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.util.Date
import javax.crypto.KeyGenerator
import javax.inject.Inject

/**
 * The fail-closed contract of the data layer against the real Room database, the
 * production `SymmetricKeyUtils` binding and the real `SessionKeyHolder`: a locked vault yields
 * empty lists and `SessionLockedException` on single reads, never a crash and never plaintext;
 * the right key reads everything back and a different key fails loudly on the GCM tag.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionLockedDataLayerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: SafeBoxDatabase

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

    @Inject
    lateinit var sessionKeyHolder: SessionKeyHolder

    @Inject
    lateinit var keystoreGateway: FakeKeystoreGateway

    @Before
    fun setUp() {
        hiltRule.inject()
        database.clearAllTables()
        sessionKeyHolder.clearSessionKey()
    }

    private fun openLegacySession() {
        sessionKeyHolder.setLegacySession(keystoreGateway.getOrCreateLegacyVaultKey())
    }

    private suspend fun seedOneRecordOfEachType() {
        loginDataRepository.upsertLoginData(
            LoginData(
                id = 1,
                title = "Login",
                url = "https://example.com",
                userId = "user@example.com",
                password = "Secret#01",
                notes = null,
                creationDate = SEED_DATE,
                updateDate = SEED_DATE,
            ),
        )
        bankAccountDataRepository.upsertBankAccountData(
            BankAccountData(
                id = 2,
                title = "Account",
                accountNo = "9876543210",
                customerName = "Jane Doe",
                customerId = null,
                branchCode = null,
                branchName = null,
                branchAddress = null,
                ifscCode = null,
                micrCode = null,
                notes = null,
                creationDate = SEED_DATE,
                updateDate = SEED_DATE,
            ),
        )
        bankCardDataRepository.upsertBankCardData(
            CardData(
                id = 3,
                title = "Card",
                name = "Jane Doe",
                number = "4111222233334444",
                expiryDate = "1228",
                cvv = "999",
                pin = "1234",
                notes = null,
                creationDate = SEED_DATE,
                updateDate = SEED_DATE,
            ),
        )
        secureNoteDataRepository.upsertSecureNoteData(
            NoteData(id = 4, title = "Note", notes = "body", creationDate = SEED_DATE, updateDate = SEED_DATE),
        )
        authenticatorDataRepository.upsertAuthenticatorData(
            AuthenticatorData(
                id = 5,
                title = "Authenticator",
                config = TotpConfig(secretKey = "JBSWY3DPEHPK3PXP"),
                creationDate = SEED_DATE,
                updateDate = SEED_DATE,
            ),
        )
    }

    @Test
    fun writingARecord_whileLocked_failsClosedWithoutTouchingTheDatabase() = runBlocking {
        val thrown = runCatching {
            loginDataRepository.upsertLoginData(
                LoginData(1, "Login", null, "user", "pw", null, SEED_DATE, SEED_DATE),
            )
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(SessionLockedException::class.java)
        assertThat(database.loginDataDao().exportAllData()).isEmpty()
    }

    @Test
    fun recordsWrittenInALegacySession_readBackThroughEveryListAndSingleRead() = runBlocking {
        openLegacySession()
        seedOneRecordOfEachType()

        assertThat(loginDataRepository.getAllLoginData().first().single().userId).isEqualTo("user@example.com")
        assertThat(bankAccountDataRepository.getAllBankAccountData().first()).hasSize(1)
        assertThat(bankCardDataRepository.getAllBankCardData().first()).hasSize(1)
        assertThat(secureNoteDataRepository.getAllSecureNoteData().first()).hasSize(1)
        assertThat(authenticatorDataRepository.getAllAuthenticatorData().first().single().secretKey)
            .isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(loginDataRepository.getLoginDataByKey(1).password).isEqualTo("Secret#01")
        // Ciphertext on disk: the encrypted columns never hold the plaintext.
        assertThat(database.loginDataDao().exportAllData().single().password).isNotEqualTo("Secret#01")
    }

    @Test
    fun afterTheVaultLocks_everyListEmitsEmptyAndSingleReadsThrow() = runBlocking {
        openLegacySession()
        seedOneRecordOfEachType()

        sessionKeyHolder.clearSessionKey()

        assertThat(loginDataRepository.getAllLoginData().first()).isEmpty()
        assertThat(bankAccountDataRepository.getAllBankAccountData().first()).isEmpty()
        assertThat(bankCardDataRepository.getAllBankCardData().first()).isEmpty()
        assertThat(authenticatorDataRepository.getAllAuthenticatorData().first()).isEmpty()
        val thrown = runCatching { loginDataRepository.getLoginDataByKey(1) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SessionLockedException::class.java)
        // Nothing was lost: the rows are still there for the next session.
        assertThat(database.loginDataDao().exportAllData()).hasSize(1)
    }

    @Test
    fun reopeningWithTheSameLegacyKey_readsTheRecordsBack() = runBlocking {
        openLegacySession()
        seedOneRecordOfEachType()
        sessionKeyHolder.clearSessionKey()

        openLegacySession()

        assertThat(loginDataRepository.getLoginDataByKey(1).password).isEqualTo("Secret#01")
        assertThat(loginDataRepository.getAllLoginData().first()).hasSize(1)
    }

    @Test
    fun aDifferentKey_failsOnTheGcmTagInsteadOfReadingGarbage() = runBlocking {
        openLegacySession()
        seedOneRecordOfEachType()
        sessionKeyHolder.clearSessionKey()
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        sessionKeyHolder.setLegacySession(otherKey)

        val single = runCatching { loginDataRepository.getLoginDataByKey(1) }.exceptionOrNull()
        assertThat(single).isInstanceOf(GeneralSecurityException::class.java)
        // A broken row is not a locked vault: the list propagates the failure instead of hiding it.
        val list = runCatching { loginDataRepository.getAllLoginData().first() }.exceptionOrNull()
        assertThat(list).isInstanceOf(GeneralSecurityException::class.java)
    }

    private companion object {
        val SEED_DATE = Date(1700000000000L)
    }
}
