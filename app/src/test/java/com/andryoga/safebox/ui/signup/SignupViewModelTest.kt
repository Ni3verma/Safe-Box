@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.signup

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.signup.SignupViewModel.Constants.MIN_NUMERIC_COUNT
import com.andryoga.safebox.ui.signup.SignupViewModel.Constants.MIN_PASSWORD_LENGTH
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignupViewModelTest {
    @MockK(relaxUnitFun = true)
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsRepository: UserDetailsRepository

    @MockK(relaxUnitFun = true)
    lateinit var analyticsHelper: AnalyticsHelper

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SignupViewModel


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = SignupViewModel(
            encryptedPreferenceProvider = encryptedPreferenceProvider,
            userDetailsRepository = userDetailsRepository,
            analyticsHelper = analyticsHelper,
            isDebug = false
        )
    }

    @Test
    fun initialState() {
        val uiState = viewModel.uiState.value

        assert(uiState.password.isBlank())
        assertFalse(uiState.isPasswordFieldError)
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
        assert(uiState.hint.isBlank())
        assertFalse(uiState.isSignupButtonEnabled)
        assertFalse(viewModel.navigateToHome.value)
    }

    @Test
    fun debugApp_initialState() {
        viewModel = SignupViewModel(
            encryptedPreferenceProvider = encryptedPreferenceProvider,
            userDetailsRepository = userDetailsRepository,
            analyticsHelper = analyticsHelper,
            isDebug = true
        )
        val uiState = viewModel.uiState.value

        assertThat(uiState.password).isEqualTo("Qwerty@@135")
        assertFalse(uiState.isPasswordFieldError)
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(uiState.hint).isEqualTo("This is a hint")
        assertTrue(uiState.isSignupButtonEnabled)
        assertFalse(viewModel.navigateToHome.value)
    }

    @Test
    fun updatePassword_updatesUiState() = runTest {
        val password = "safasf"
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.password).isEqualTo(password)
    }

    @Test
    fun onPasswordUpdate_fails_emptyPassword() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = ""))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.EMPTY_PASSWORD)
        assertTrue(uiState.isPasswordFieldError)
        assertFalse(uiState.isSignupButtonEnabled)
    }

    @Test
    fun onPasswordUpdate_fails_onlyLowerCaseLetters() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "jsanfjakf"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        assertTrue(uiState.isPasswordFieldError)
        assertFalse(uiState.isSignupButtonEnabled)
    }

    @Test
    fun onPasswordUpdate_fails_onlyUpperCaseLetters() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "FMNJKFBNJ"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        assertTrue(uiState.isPasswordFieldError)
        assertFalse(uiState.isSignupButtonEnabled)
    }

    @Test
    fun onPasswordUpdate_fails_withLessThanMinNumericCount() = runTest {
        var password = "jaLO" // no numeric and mix and upper, lower case

        repeat(MIN_NUMERIC_COUNT - 1) {
            password += "1" // add a number to the password
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.LESS_NUMERIC_COUNT)
            assertTrue(uiState.isPasswordFieldError)
            assertFalse(uiState.isSignupButtonEnabled)
        }
    }

    @Test
    fun onPasswordUpdate_fails_withEqOrGtMinNumericCount() = runTest {
        var password = "jaLO" // no numeric and mix and upper, lower case
        repeat(MIN_NUMERIC_COUNT) {
            password += "1" // add a number to the password
        }

        repeat(2) {
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NO_SPECIAL_CHAR)
            assertTrue(uiState.isPasswordFieldError)
            assertFalse(uiState.isSignupButtonEnabled)
            password += "1" // add a number to the password
        }
    }

    @Test
    fun onPasswordUpdate_fails_withLengthLessThanMinLength() = runTest {
        var password = "jJ@"
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        repeat(MIN_NUMERIC_COUNT) {
            password += "1"
        }

        while (password.length < MIN_PASSWORD_LENGTH) {
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.SHORT_PASSWORD_LENGTH)
            assertTrue(uiState.isPasswordFieldError)
            assertFalse(uiState.isSignupButtonEnabled)
            password += charPool.random()
        }
    }

    @Test
    fun onPasswordUpdate_passes_withLengthEqOrGtThanMinLength() = runTest {
        var password = "jJ12@"
        val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        while (password.length != MIN_PASSWORD_LENGTH) {
            password += charPool.random()
        }

        repeat(2) {
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
            assertFalse(uiState.isPasswordFieldError)
            assertFalse(uiState.isSignupButtonEnabled)
            password += charPool.random()
        }
    }

    @Test
    fun validPasswordUpdate_afterInvalidPassword_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJJ"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJ@43jsnfjka"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertFalse(uiState.isPasswordFieldError)
        assertFalse(uiState.isSignupButtonEnabled)
    }

    @Test
    fun invalidPasswordUpdate_afterValidPassword_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJ@43jsnfjka"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJJ"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isNotEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertTrue(uiState.isPasswordFieldError)
        assertFalse(uiState.isSignupButtonEnabled)
    }

    @Test
    fun updateHint_updatesUiState() = runTest {
        val hint = "this is hint"
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = hint))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.hint).isEqualTo(hint)
    }

    @Test
    fun updateHint_emptyHint_doesNotEnabledSignupButtonState() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "  "))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun updateHint_withoutPassword_doesNotEnabledSignupButtonState() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun updateHint_shouldNotUpdatePasswordState() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isPasswordFieldError).isFalse() // because password is not yet entered
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
    }

    @Test
    fun updateHintMultipleTimes_shouldNotUpdatePasswordState() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint 2"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isPasswordFieldError).isFalse() // because password is not yet entered
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
    }

    @Test
    fun updateHint_withInvalidPassword_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "djD7b"))
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.passwordValidatorState).isNotIn(
            listOf(
                PasswordValidatorState.INITIAL_STATE,
                PasswordValidatorState.PASSWORD_IS_OK
            )
        )
    }

    @Test
    fun updateHint_withValidPassword_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj@687JJdd")) // valid password
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isTrue()
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
    }

    @Test
    fun validPassword_afterHint_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "da")) // invalid password
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint")) // hint is entered
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj@687JJdd")) // now valid password
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isTrue()
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
    }

    @Test
    fun invalidPassword_afterHintAndValidPassword_updatesStateCorrectly() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj@687JJdd")) // valid password
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint")) // hint is entered
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "da")) // invalid password
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.passwordValidatorState).isNotEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
    }

    @Test
    fun signup_invalidPassword_sendAnalyticsEventAndDoesNotMakeDbCall() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj"))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        verify(exactly = 1) {
            analyticsHelper.logEvent(key = AnalyticsKey.SIGNUP_BLOCKED)
        }
        coVerify(exactly = 0) {
            userDetailsRepository.insertUserDetailsData(any(), any())
        }
        assertThat(uiState.passwordValidatorState).isNotEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun signup_emptyHint_sendAnalyticsEventAndDoesNotMakeDbCall() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = ""))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsHelper.logEvent(key = AnalyticsKey.SIGNUP_BLOCKED)
        }
        coVerify(exactly = 0) {
            userDetailsRepository.insertUserDetailsData(any(), any())
        }
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun signup_validPasswordButEmptyHint_sendAnalyticsEventAndDoesNotMakeDbCall() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj@687JJdd"))
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = ""))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsHelper.logEvent(key = AnalyticsKey.SIGNUP_BLOCKED)
        }
        coVerify(exactly = 0) {
            userDetailsRepository.insertUserDetailsData(any(), any())
        }
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun signup_BlankHint_sendAnalyticsEventAndDoesNotMakeDbCall() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "  "))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        verify(exactly = 1) {
            analyticsHelper.logEvent(key = AnalyticsKey.SIGNUP_BLOCKED)
        }
        coVerify(exactly = 0) {
            userDetailsRepository.insertUserDetailsData(any(), any())
        }
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun signup_validPasswordAndHint() = runTest {
        val hint = "a"
        val password = "aP@33dsdasP"
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = hint))
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            analyticsHelper.logEvent(key = AnalyticsKey.SIGN_UP)
            userDetailsRepository.insertUserDetailsData(password, hint)
            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                false
            )
        }
        assertThat(viewModel.navigateToHome.value).isTrue()
    }
}