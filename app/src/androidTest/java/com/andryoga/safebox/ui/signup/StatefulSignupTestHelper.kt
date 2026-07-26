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

    val validatorState = if (password.isEmpty()) {
        PasswordValidatorState.INITIAL_STATE
    } else {
        SignupViewModel.runPasswordValidator(password)
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
