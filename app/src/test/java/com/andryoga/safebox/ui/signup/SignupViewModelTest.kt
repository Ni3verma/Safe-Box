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
    fun `verify initial state of ui state`() {
        val uiState = viewModel.uiState.value

        assertThat(uiState.password).isEmpty()
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
        assertThat(uiState.hint).isEmpty()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun `verify initial state of ui state for debug app`() {
        viewModel = SignupViewModel(
            encryptedPreferenceProvider = encryptedPreferenceProvider,
            userDetailsRepository = userDetailsRepository,
            analyticsHelper = analyticsHelper,
            isDebug = true
        )
        val uiState = viewModel.uiState.value

        assertThat(uiState.password).isEqualTo("Qwerty@@135")
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(uiState.hint).isEqualTo("This is a hint")
        assertThat(uiState.isSignupButtonEnabled).isTrue()
        assertThat(viewModel.navigateToHome.value).isFalse()
    }

    @Test
    fun `password is updated in ui state when password update screen action comes to vm`() =
        runTest {
        val password = "safasf"
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.password).isEqualTo(password)
    }

    @Test
    fun `on password update with empty password fails validation`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = ""))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.EMPTY_PASSWORD)
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on password update with only lowercase letters fails validation`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "jsanfjakf"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on password update with only uppercase letters fails validation`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "FMNJKFBNJ"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NOT_MIX_CASE)
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on password update with less than minimum numeric count fails validation`() = runTest {
        var password = "jaLO" // no numeric and mix and upper, lower case

        repeat(MIN_NUMERIC_COUNT - 1) {
            password += "1" // add a number to the password
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.LESS_NUMERIC_COUNT)
            assertThat(uiState.isPasswordFieldError).isTrue()
            assertThat(uiState.isSignupButtonEnabled).isFalse()
        }
    }

    @Test
    fun `on password update with minimum numeric count but no special char fails validation`() =
        runTest {
        var password = "jaLO" // no numeric and mix and upper, lower case
        repeat(MIN_NUMERIC_COUNT) {
            password += "1" // add a number to the password
        }

        repeat(2) {
            viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
            advanceUntilIdle()
            val uiState = viewModel.uiState.value
            assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.NO_SPECIAL_CHAR)
            assertThat(uiState.isPasswordFieldError).isTrue()
            assertThat(uiState.isSignupButtonEnabled).isFalse()
            password += "1" // add a number to the password
        }
    }

    @Test
    fun `on password update with password shorter than min length fails validation`() = runTest {
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
            assertThat(uiState.isPasswordFieldError).isTrue()
            assertThat(uiState.isSignupButtonEnabled).isFalse()
            password += charPool.random()
        }
    }

    @Test
    fun `on password update with valid password passes validation`() = runTest {
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
            assertThat(uiState.isPasswordFieldError).isFalse()
            assertThat(uiState.isSignupButtonEnabled).isFalse()
            password += charPool.random()
        }
    }

    @Test
    fun `on password update with valid password after invalid password updates state correctly`() =
        runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJJ"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJ@43jsnfjka"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on password update with invalid password after valid password updates state correctly`() =
        runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJ@43jsnfjka"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "aJJ"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.passwordValidatorState).isNotEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
        assertThat(uiState.isPasswordFieldError).isTrue()
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on hint update updates ui state`() = runTest {
        val hint = "this is hint"
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = hint))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.hint).isEqualTo(hint)
    }

    @Test
    fun `on hint update with empty hint does not enable signup button`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "  "))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on hint update without password does not enable signup button`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isFalse()
    }

    @Test
    fun `on hint update does not update password state`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isPasswordFieldError).isFalse() // because password is not yet entered
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
    }

    @Test
    fun `on multiple hint updates do not update password state`() = runTest {
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint 2"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isPasswordFieldError).isFalse() // because password is not yet entered
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.INITIAL_STATE)
    }

    @Test
    fun `on hint update with invalid password updates state correctly`() = runTest {
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
    fun `on hint update with valid password updates state correctly and enables signup`() =
        runTest {
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = "dj@687JJdd")) // valid password
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = "this is hint"))
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertThat(uiState.isSignupButtonEnabled).isTrue()
        assertThat(uiState.isPasswordFieldError).isFalse()
        assertThat(uiState.passwordValidatorState).isEqualTo(PasswordValidatorState.PASSWORD_IS_OK)
    }

    @Test
    fun `on valid password update after hint updates state correctly`() = runTest {
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
    fun `on invalid password update after hint and valid password updates state correctly`() =
        runTest {
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
    fun `on signup with invalid password logs analytics and does not call db`() = runTest {
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
    fun `on signup with empty hint logs analytics and does not call db`() = runTest {
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
    fun `on signup with valid password but empty hint logs analytics and does not call db`() =
        runTest {
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
    fun `on signup with blank hint logs analytics and does not call db`() = runTest {
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
    fun `on signup with valid password and hint saves data and navigates`() = runTest {
        val hint = "a"
        val password = "aP@33dsdasP"
        viewModel.onAction(action = SignupScreenAction.OnHintUpdate(hint = hint))
        viewModel.onAction(action = SignupScreenAction.OnPasswordUpdate(password = password))
        viewModel.onAction(action = SignupScreenAction.OnSignupClick)
        advanceUntilIdle()

        verify(exactly = 1) { analyticsHelper.logEvent(key = AnalyticsKey.SIGN_UP) }
        coVerify(exactly = 1) { userDetailsRepository.insertUserDetailsData(password, hint) }
        coVerify(exactly = 1) {
            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                false
            )
        }
        assertThat(viewModel.navigateToHome.value).isTrue()
    }
}
