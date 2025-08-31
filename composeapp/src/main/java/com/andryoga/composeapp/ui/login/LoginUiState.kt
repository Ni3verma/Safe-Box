package com.andryoga.composeapp.ui.login

data class LoginUiState(
    val hint: String = "",
    val passwordValidatorState: PasswordValidatorState = PasswordValidatorState.INITIAL
)
