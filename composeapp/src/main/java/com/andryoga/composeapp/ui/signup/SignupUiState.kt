package com.andryoga.composeapp.ui.signup

data class SignupUiState(
    val password: String = "",
    val isPasswordFieldError: Boolean = false,
    val passwordValidatorState: PasswordValidatorState = PasswordValidatorState.INITIAL_STATE,

    val hint: String = "",

    val isSignupButtonEnabled: Boolean = false,
    val navigateToHome: Boolean = false
)
