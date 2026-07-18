package com.andryoga.safebox.ui.signup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Shared component test double stateful host wrapper mimicking [SignupViewModel] Unidirectional Data Flow.
 * Evaluates password validation state and dynamically enables/disables the Signup button.
 * Shared between [SignupScreenTest] and [SignupPasswordValidationRulesTest] to eliminate code duplication.
 */
@Composable
fun StatefulSignupScreenTestHost(
    onSignupClicked: () -> Unit = {},
    screenActionObserver: (SignupScreenAction) -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    val validatorState = run {
        var hasLowerCase = false
        var hasUpperCase = false
        var numericCount = 0
        var specialCharCount = 0

        password.forEach { char ->
            when {
                char.isLowerCase() -> hasLowerCase = true
                char.isUpperCase() -> hasUpperCase = true
                char.isDigit() -> numericCount++
                !char.isLetterOrDigit() -> specialCharCount++
            }
        }
        when {
            password.isBlank() -> PasswordValidatorState.INITIAL_STATE
            hasLowerCase.not() || hasUpperCase.not() -> PasswordValidatorState.NOT_MIX_CASE
            numericCount < 2 -> PasswordValidatorState.LESS_NUMERIC_COUNT
            specialCharCount == 0 -> PasswordValidatorState.NO_SPECIAL_CHAR
            password.length < 7 -> PasswordValidatorState.SHORT_PASSWORD_LENGTH
            else -> PasswordValidatorState.PASSWORD_IS_OK
        }
    }

    val isError =
        validatorState != PasswordValidatorState.PASSWORD_IS_OK && validatorState != PasswordValidatorState.INITIAL_STATE
    val isButtonEnabled =
        validatorState == PasswordValidatorState.PASSWORD_IS_OK && hint.isNotBlank()

    SafeBoxTheme {
        SignupScreen(
            uiState = SignupUiState(
                password = password,
                hint = hint,
                isPasswordFieldError = isError,
                passwordValidatorState = validatorState,
                isSignupButtonEnabled = isButtonEnabled
            ),
            screenAction = { action ->
                screenActionObserver(action)
                when (action) {
                    is SignupScreenAction.OnPasswordUpdate -> password = action.password
                    is SignupScreenAction.OnHintUpdate -> hint = action.hint
                    SignupScreenAction.OnSignupClick -> onSignupClicked()
                }
            }
        )
    }
}
