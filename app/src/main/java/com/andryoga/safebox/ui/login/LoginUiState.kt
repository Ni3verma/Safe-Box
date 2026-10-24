package com.andryoga.safebox.ui.login

import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationProgressState
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.ui.core.SessionLockReason

/**
 * Describes a failed legacy-to-envelope vault migration displayed on the Login screen.
 *
 * @property reason Structured failure reason.
 * @property trigger Which user path initiated the migration.
 * @property attempt 1-based attempt ordinal within the current process.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
data class MigrationFailureUiState(
    val reason: MigrationFailureReason,
    val trigger: MigrationTrigger,
    val attempt: Int = 1,
) {
    /**
     * Whether the user can defer the upgrade and enter a legacy session. Hidden for
     * [MigrationTrigger.FORGOT_PASSWORD] because the user does not know the old password and the
     * new password was not saved.
     */
    val canContinueLegacy: Boolean
        get() = trigger == MigrationTrigger.LOGIN
}

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
    /** True while the vault on disk is still at `crypto_version = 1`; shows the upgrade notice and suppresses biometric login. */
    // TODO(D3): legacy vault (crypto_version = 1) only
    val isLegacyVault: Boolean = false,
    /** Non-dismissable determinate progress state while a legacy vault is being migrated. */
    // TODO(D3): legacy vault (crypto_version = 1) only
    val migrationProgress: MigrationProgressState = MigrationProgressState.Idle,
    /** Non-null when the most recent legacy migration attempt failed. */
    // TODO(D3): legacy vault (crypto_version = 1) only
    val migrationFailure: MigrationFailureUiState? = null,
)
