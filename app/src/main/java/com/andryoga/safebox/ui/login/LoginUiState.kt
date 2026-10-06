package com.andryoga.safebox.ui.login

data class LoginUiState(
    val hint: String = "",
    val canUnlockWithBiometric: Boolean = false,
    val userAuthState: UserAuthState = UserAuthState.INITIAL,
    val defaultPassword: String = "",
    /**
     * Number of password attempts rejected so far. [userAuthState] stays at
     * [UserAuthState.INCORRECT_PASSWORD_ENTERED] across consecutive failures, so the UI keys its
     * per-rejection feedback (shake and haptic) on this counter instead.
     */
    val failedLoginAttempts: Int = 0,
)
