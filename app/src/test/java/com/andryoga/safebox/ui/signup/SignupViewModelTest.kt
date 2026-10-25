@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.signup

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignupViewModelTest {

    @MockK(relaxUnitFun = true)
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @MockK
    lateinit var vaultRepository: VaultRepository

    @MockK(relaxUnitFun = true)
    lateinit var analyticsHelper: AnalyticsHelper

    @MockK(relaxUnitFun = true)
    lateinit var vaultKeyMaintenance: VaultKeyMaintenance

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var viewModel: SignupViewModel
    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        sessionKeyHolder = SessionKeyHolderImpl()
        coEvery { vaultRepository.hasVault() } returns false
        coEvery { vaultRepository.createVault(any(), any()) } answers { testDek.copyOf() }
        viewModel = createViewModel(isDebug = false)
    }

    private fun createViewModel(isDebug: Boolean) = SignupViewModel(
        encryptedPreferenceProvider = encryptedPreferenceProvider,
        vaultRepository = vaultRepository,
        analyticsHelper = analyticsHelper,
        sessionKeyHolder = sessionKeyHolder,
        vaultKeyMaintenance = vaultKeyMaintenance,
        isDebug = isDebug,
    )

    @Test
    fun initialUiState_matchesDefaultSignupUiState() = runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.password).isEmpty()
            assertThat(initialState.hint).isEmpty()
            assertThat(initialState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
            assertThat(initialState.isPasswordFieldError).isFalse()
            assertThat(initialState.isSignupButtonEnabled).isFalse()
        }
    }

    @Test
    fun initialNavigateToHome_stateIsFalse() = runTest {
        viewModel.navigateToHome.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun initialUiState_whenIsDebugTrue_prefillsDebugPasswordAndHint() = runTest {
        viewModel = createViewModel(isDebug = true)
        advanceUntilIdle()
        val uiState = viewModel.uiState.value

        assertThat(uiState.password).isEqualTo("Qwerty@@135")
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(uiState.hint).isEqualTo("first 5 @@ first 3")
        assertThat(uiState.isSignupButtonEnabled).isTrue()
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun onPasswordUpdate_withEmptyText_updatesStateWithEmptyPassword() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = ""))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.password).isEmpty()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.EMPTY_PASSWORD)
            assertThat(updatedState.isPasswordFieldError).isTrue()
        }
    }

    @Test
    fun onPasswordUpdate_withLengthLessThan7_updatesStateWithShortPasswordLength() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Ab12@"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.SHORT_PASSWORD_LENGTH)
        }
    }

    @Test
    fun onPasswordUpdate_withNoSpecialChar_updatesStateWithNoSpecialChar() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password123"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.NO_SPECIAL_CHAR)
        }
    }

    @Test
    fun onPasswordUpdate_withNoUpperCase_updatesStateWithNotMixCase() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "password@123"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        }
    }

    @Test
    fun onPasswordUpdate_withNoLowerCase_updatesStateWithNotMixCase() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "PASSWORD@123"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        }
    }

    @Test
    fun onPasswordUpdate_withLessNumericCount_updatesStateWithLessNumericCount() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password@1"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.LESS_NUMERIC_COUNT)
        }
    }

    @Test
    fun onPasswordUpdate_withValidPassword_updatesStateWithPasswordIsOk() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password@123"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
            assertThat(updatedState.isPasswordFieldError).isFalse()
        }
    }

    @Test
    fun onHintUpdate_updatesHintInUiState() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAction(SignupScreenAction.OnHintUpdate(hint = "favorite pet"))
            advanceUntilIdle()

            val updatedState = expectMostRecentItem()
            assertThat(updatedState.hint).isEqualTo("favorite pet")
        }
    }

    @Test
    fun onSignupClick_withValidPasswordAndHint_createsEnvelopeVaultOpensDekSessionAndNavigatesHome() = runTest {
        val password = "Password@123"
        val hint = "favorite pet"

        viewModel.navigateToHome.test {
            assertThat(awaitItem()).isFalse()

            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = password))
            viewModel.onAction(SignupScreenAction.OnHintUpdate(hint = hint))
            viewModel.onAction(SignupScreenAction.OnSignupClick)
            advanceUntilIdle()

            verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.SIGN_UP) }
            coVerify(exactly = 1) { vaultRepository.hasVault() }
            coVerify(exactly = 1) {
                vaultRepository.createVault(
                    match { it.contentEquals(password.toCharArray()) },
                    hint,
                )
            }
            assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
            assertThat(sessionKeyHolder.isLegacySession).isFalse()
            verify(exactly = 1) { vaultKeyMaintenance.scheduleAfterUnlock() }
            coVerify(exactly = 1) {
                encryptedPreferenceProvider.upsertBooleanPref(
                    CommonConstants.IS_SIGN_UP_REQUIRED,
                    false,
                )
            }
            assertThat(expectMostRecentItem()).isTrue()
        }
    }

    @Test
    fun onSignupClick_whenCalledTwiceWhileInFlight_createsVaultOnlyOnce() = runTest {
        viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password@123"))
        viewModel.onAction(SignupScreenAction.OnHintUpdate(hint = "favorite pet"))

        viewModel.onAction(SignupScreenAction.OnSignupClick)
        viewModel.onAction(SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.SIGN_UP) }
        coVerify(exactly = 1) { vaultRepository.createVault(any(), any()) }
    }

    @Test
    fun onSignupClick_whenVaultAlreadyExists_leavesVaultAndSessionUntouched() {
        coEvery { vaultRepository.hasVault() } returns true

        val thrown = runCatching {
            runTest {
                viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password@123"))
                viewModel.onAction(SignupScreenAction.OnHintUpdate(hint = "favorite pet"))
                viewModel.onAction(SignupScreenAction.OnSignupClick)
                advanceUntilIdle()
            }
        }.exceptionOrNull()

        val debugFatal = generateSequence(thrown) { it.cause }
            .firstOrNull { it is Exceptions.DebugFatalException }
        assertThat(debugFatal).isNotNull()
        coVerify(exactly = 0) { vaultRepository.createVault(any(), any()) }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        verify(exactly = 0) { vaultKeyMaintenance.scheduleAfterUnlock() }
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun onSignupClick_whenBlocked_leavesVaultAndSessionUntouched() = runTest {
        viewModel.onAction(SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        coVerify(exactly = 0) { vaultRepository.createVault(any(), any()) }
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun onSignupClick_withInvalidPassword_logsSignupBlockedAndDoesNotCreateVault() = runTest {
        viewModel.navigateToHome.test {
            assertThat(awaitItem()).isFalse()

            viewModel.onAction(SignupScreenAction.OnSignupClick)
            advanceUntilIdle()

            verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.SIGNUP_BLOCKED) }
            coVerify(exactly = 0) { vaultRepository.createVault(any(), any()) }
            expectNoEvents()
        }
    }

    @Test
    fun onSignupClick_withEmptyHint_logsSignupBlockedAndDoesNotCreateVault() = runTest {
        viewModel.navigateToHome.test {
            assertThat(awaitItem()).isFalse()

            viewModel.onAction(SignupScreenAction.OnPasswordUpdate(password = "Password@123"))
            viewModel.onAction(SignupScreenAction.OnHintUpdate(hint = ""))
            viewModel.onAction(SignupScreenAction.OnSignupClick)
            advanceUntilIdle()

            verify(exactly = 1) { analyticsHelper.logEvent(AnalyticsKey.SIGNUP_BLOCKED) }
            coVerify(exactly = 0) { vaultRepository.createVault(any(), any()) }
            expectNoEvents()
        }
    }
}
