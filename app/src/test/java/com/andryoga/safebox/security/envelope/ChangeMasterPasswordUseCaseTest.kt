@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.WrongPasswordException
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [ChangeMasterPasswordUseCase]: the current-password gate and the Settings re-wrap.
 */
class ChangeMasterPasswordUseCaseTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var vaultRepository: VaultRepository

    @MockK
    lateinit var envelopeEncryptionManager: EnvelopeEncryptionManager

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var preferenceProvider: FakePreferenceProvider
    private lateinit var useCase: ChangeMasterPasswordUseCase

    private val sessionDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }
    private val envelopeState = VaultState.Envelope(
        kdfParams = KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = 99_000,
            salt = ByteArray(16) { 7 },
        ),
        wrappedDekPassword = "password-wrap",
        dekVerifier = "verifier",
        wrappedDekBiometric = null,
        biometricKeyAliasSuffix = null,
        wrappedDekDevice = null,
        deviceKeyAliasSuffix = null,
        isBiometricUnlockDisabled = false,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        sessionKeyHolder = SessionKeyHolderImpl()
        preferenceProvider = FakePreferenceProvider()
        coEvery { vaultRepository.getVaultState() } returns envelopeState
        coEvery { settingsDataStore.getPasswordAfterXBiometricLogins() } returns 7
        useCase = ChangeMasterPasswordUseCase(
            vaultRepository = vaultRepository,
            envelopeEncryptionManager = envelopeEncryptionManager,
            sessionKeyHolder = sessionKeyHolder,
            preferenceProvider = preferenceProvider,
            settingsDataStore = settingsDataStore,
            dispatchersProvider = mainDispatcherRule.testDispatcherProvider,
        )
    }

    private fun givenUnwrapReturns(dek: ByteArray) {
        every {
            envelopeEncryptionManager.unwrapWithPassword(any(), envelopeState.kdfParams, envelopeState.wrappedDekPassword)
        } returns Result.success(dek)
    }

    @Test
    fun verifyCurrentPassword_whenPasswordUnwrapsTheRealDek_shouldPassAndZeroTheDek() = runTest {
        val unwrapped = sessionDek.copyOf()
        givenUnwrapReturns(unwrapped)
        every { envelopeEncryptionManager.verify(unwrapped, envelopeState.dekVerifier) } returns true

        assertThat(useCase.verifyCurrentPassword("Current@@123".toCharArray())).isTrue()
        assertThat(unwrapped.all { it == 0.toByte() }).isTrue()
    }

    @Test
    fun verifyCurrentPassword_whenPasswordIsWrong_shouldFail() = runTest {
        every { envelopeEncryptionManager.unwrapWithPassword(any(), any(), any()) } returns
            Result.failure(WrongPasswordException())

        assertThat(useCase.verifyCurrentPassword("Wrong@@123".toCharArray())).isFalse()
    }

    @Test
    fun verifyCurrentPassword_whenVerifierRejectsTheDek_shouldFailAndZeroTheDek() = runTest {
        val unwrapped = sessionDek.copyOf()
        givenUnwrapReturns(unwrapped)
        every { envelopeEncryptionManager.verify(unwrapped, envelopeState.dekVerifier) } returns false

        assertThat(useCase.verifyCurrentPassword("Current@@123".toCharArray())).isFalse()
        assertThat(unwrapped.all { it == 0.toByte() }).isTrue()
    }

    @Test
    fun verifyCurrentPassword_whenVaultStateIsUnreadable_shouldFailClosed() = runTest {
        coEvery { vaultRepository.getVaultState() } throws IOException("disk")

        assertThat(useCase.verifyCurrentPassword("Current@@123".toCharArray())).isFalse()
    }

    @Test
    fun verifyCurrentPassword_onALegacyVault_shouldAssertInDebugBuilds() = runTest {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy

        val outcome = runCatching { useCase.verifyCurrentPassword("Current@@123".toCharArray()) }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(Exceptions.DebugFatalException::class.java)
    }

    @Test
    fun changePassword_shouldRewrapTheSessionDekWithTheHintAndZeroBothSecrets() = runTest {
        sessionKeyHolder.setSessionKey(sessionDek.copyOf())
        val newPassword = "NewPass@@123".toCharArray()
        val dekSlot = slot<ByteArray>()
        var dekAtRewrap: ByteArray? = null
        var passwordAtRewrap: String? = null
        coEvery { vaultRepository.rewrapPassword(any(), capture(dekSlot), "new hint") } answers {
            dekAtRewrap = dekSlot.captured.copyOf()
            passwordAtRewrap = String(firstArg<CharArray>())
        }

        useCase.changePassword(newPassword, "new hint")

        assertThat(dekAtRewrap).isEqualTo(sessionDek)
        assertThat(passwordAtRewrap).isEqualTo("NewPass@@123")
        assertThat(dekSlot.captured.all { it == 0.toByte() }).isTrue()
        assertThat(newPassword.all { it == Char.MIN_VALUE }).isTrue()
        // The DEK did not change: the session stays open on it.
        assertThat(sessionKeyHolder.withSessionKey { it.encoded }).isEqualTo(sessionDek)
    }

    @Test
    fun changePassword_shouldResetTheBiometricLoginAllowance() = runTest {
        sessionKeyHolder.setSessionKey(sessionDek.copyOf())
        preferenceProvider.upsertIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, 1)

        useCase.changePassword("NewPass@@123".toCharArray(), "new hint")

        assertThat(preferenceProvider.getIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, -1))
            .isEqualTo(7)
    }

    @Test
    fun changePassword_whenSessionIsLocked_shouldThrowAndWriteNothing() = runTest {
        val outcome = runCatching { useCase.changePassword("NewPass@@123".toCharArray(), "new hint") }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(SessionLockedException::class.java)
        coVerify(exactly = 0) { vaultRepository.rewrapPassword(any(), any(), any()) }
    }

    @Test
    fun changePassword_inALegacySession_shouldRefuseAndWriteNothing() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)

        val outcome = runCatching { useCase.changePassword("NewPass@@123".toCharArray(), "new hint") }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        coVerify(exactly = 0) { vaultRepository.rewrapPassword(any(), any(), any()) }
    }

    @Test
    fun changePassword_whenRewrapFails_shouldPropagateAndStillZeroTheDekCopy() = runTest {
        sessionKeyHolder.setSessionKey(sessionDek.copyOf())
        val dekSlot = slot<ByteArray>()
        coEvery { vaultRepository.rewrapPassword(any(), capture(dekSlot), any()) } throws IOException("disk full")

        val outcome = runCatching { useCase.changePassword("NewPass@@123".toCharArray(), "new hint") }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(dekSlot.captured.all { it == 0.toByte() }).isTrue()
        assertThat(preferenceProvider.getIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, -1))
            .isEqualTo(-1)
    }
}
