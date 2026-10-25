package com.andryoga.safebox.ui.login

import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationProgressState
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.ui.core.SessionLockReason
import javax.crypto.Cipher

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

/**
 * Why a forgot-password recovery on a `crypto_version = 2` vault ended without a new password
 * (design §6.7). The password wrap is never written on any of these.
 *
 * @property wireName Analytics value of [com.andryoga.safebox.common.AnalyticsParam.REASON]; append-only.
 */
enum class RecoveryFailureReason(val wireName: String) {
    /** API 29: the time-bound device key refused the unwrap again after the second prompt. */
    NOT_AUTHENTICATED("not_authenticated"),

    /** The unwrapped bytes are not this vault's DEK (`dek_verifier` mismatch); the device wrap was discarded. */
    VERIFIER("verifier"),

    /** The device key could not unwrap after a successful prompt; the device wrap was discarded. */
    KEYSTORE("keystore"),

    /** The new password wrap could not be saved; the vault is exactly as before. */
    STORAGE("storage"),
}

/**
 * Where the forgot-password flow is. Driven by the ViewModel so the screen shows exactly one
 * prompt or dialog at a time and a rotation resumes the same step.
 */
sealed interface ForgotPasswordStep {
    /**
     * Legacy vault: today's gate. The screen prompts without a `CryptoObject`, or shows
     * `DeviceSecurityRequiredDialog` when the device cannot authenticate (design §6.4.1).
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    data object LegacyAuthentication : ForgotPasswordStep

    /**
     * `crypto_version = 2` vault: the device prompt of design §6.7.
     *
     * Deliberately not a data class: every instance is a distinct prompt, including the API 29
     * re-prompt that carries the same `null` cipher, which a value-equal state would de-duplicate.
     *
     * @property cipher `DECRYPT` cipher over `wrapped_dek_device` to bind as the `CryptoObject`
     * (API 30+ per-operation key), or `null` when the time-bound key is outside its window
     * (API 29): the ViewModel then initialises the cipher right after the prompt succeeds.
     */
    class DeviceAuthentication(val cipher: Cipher?) : ForgotPasswordStep

    /** The credential was accepted (legacy) or the DEK recovered and verified (v2): collect the new password and hint. */
    data object NewPassword : ForgotPasswordStep
}

data class LoginUiState(
    val hint: String = "",
    /**
     * Single-use `DECRYPT` cipher over `wrapped_dek_biometric`, prepared by the ViewModel; non-null
     * exactly while the fingerprint prompt should be showing (design §6.6). The screen binds it as
     * the `CryptoObject` and hands back the authorised cipher in [LoginScreenAction.BiometricSuccess].
     */
    val biometricUnlockCipher: Cipher? = null,
    /** `KEK_Biometric` was found invalidated by this login screen; the banner asks for the password. */
    val isBiometricKeyInvalidated: Boolean = false,
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
    /** Non-null while a forgot-password flow is in progress. */
    val forgotPasswordStep: ForgotPasswordStep? = null,
    /** Forgot password on a `crypto_version = 2` vault with no usable device wrap: the card replaces the login form (design §6.7). */
    val isRecoveryNotSetUp: Boolean = false,
    /** Non-null after a recovery attempt failed; cleared by the next *Forgot password?* tap. */
    val recoveryFailure: RecoveryFailureReason? = null,
    /** The vault was erased (design §6.11); the screen leaves for Signup. */
    val isVaultErased: Boolean = false,
) {
    val canUnlockWithBiometric: Boolean
        get() = biometricUnlockCipher != null
}
