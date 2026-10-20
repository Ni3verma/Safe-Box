package com.andryoga.safebox.ui.login

/**
 * How the vault was unlocked. Kept separate from the repository's `withBiometric` flag so the
 * forgot-password recovery is not folded into plain password logins.
 *
 * @param analyticsValue wire value; append-only once shipped.
 */
enum class LoginMethod(val analyticsValue: String) {
    PASSWORD("password"),
    BIOMETRIC("biometric"),
    PASSWORD_RESET("password_reset"),
}
