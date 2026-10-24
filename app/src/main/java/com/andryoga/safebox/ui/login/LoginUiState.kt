package com.andryoga.safebox.ui.login

import com.andryoga.safebox.ui.core.SessionLockReason

data class LoginUiState(
    val hint: String = "",
    val canUnlockWithBiometric: Boolean = false,
    val userAuthState: UserAuthState = UserAuthState.INITIAL,
    val defaultPassword: String = "",
    /** Why the previous session ended, when whoever ended it said so; drives the banner. */
    val lockReason: SessionLockReason? = null,
    /**
     * Number of password attempts rejected so far. [userAuthState] stays at
     * [UserAuthState.INCORRECT_PASSWORD_ENTERED] across consecutive failures, so the UI keys its
     * per-rejection feedback (shake and haptic) on this counter instead.
     */
    val failedLoginAttempts: Int = 0,
)
