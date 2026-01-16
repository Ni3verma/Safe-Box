package com.andryoga.safebox.ui.login

data class LoginUiState(
    val hint: String = "",
    val canUnlockWithBiometric: Boolean = false,
    val userAuthState: UserAuthState = UserAuthState.INITIAL
)
