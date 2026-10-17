package com.andryoga.safebox.ui.login

import com.andryoga.safebox.common.AnalyticsParam

/**
 * How the vault was unlocked, as reported on `AnalyticsKey.LOGIN_SUCCESS`.
 *
 * Kept apart from the repository's `withBiometric` flag because a login that follows a master
 * password reset is neither a normal password login nor a biometric one, and conflating it with
 * `PASSWORD` would hide the recovery path in the numbers.
 *
 * @param analyticsValue wire value for [AnalyticsParam.METHOD]; append-only once shipped.
 */
enum class LoginMethod(val analyticsValue: String) {
    PASSWORD("password"),
    BIOMETRIC("biometric"),
    /** The forgot-password flow: device-credential auth, new master password, then unlocked. */
    PASSWORD_RESET("password_reset"),
}
