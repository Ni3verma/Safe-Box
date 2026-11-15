package com.andryoga.safebox.ui.home.settings

import com.andryoga.safebox.data.dataStore.Settings

/**
 * Everything the Settings screen renders: the stored preferences plus the rows that depend on the
 * vault and the device.
 *
 * @property settings Preferences from `SettingsDataStore`.
 * @property isLegacySession The vault is open in a legacy session: change-password is disabled
 * and the fingerprint switch hidden, because there is no DEK to re-wrap.
 * @property biometricUnlock The fingerprint-unlock switch.
 * @property changePasswordStep The change-password flow step on screen, `null` when idle.
 * @property passwordChangeResult Outcome of the last re-wrap, until the screen reports it shown.
 */
data class SettingsUiState(
    val settings: Settings = Settings(),
    val isLegacySession: Boolean = false,
    val biometricUnlock: BiometricUnlockSwitch = BiometricUnlockSwitch.Hidden,
    val changePasswordStep: ChangePasswordStep? = null,
    val passwordChangeResult: PasswordChangeResult? = null,
)

/** The fingerprint-unlock switch, from `BiometricManager` and `biometric_unlock_disabled`. */
sealed interface BiometricUnlockSwitch {
    /** No biometric hardware, or a legacy session. */
    data object Hidden : BiometricUnlockSwitch

    /** Hardware exists but no strong biometric is ready: shown disabled. */
    data object Unavailable : BiometricUnlockSwitch

    /** @property isOn The user has not switched fingerprint unlock off. */
    data class Available(val isOn: Boolean) : BiometricUnlockSwitch
}

/** One step of the Settings change-password flow; the screen renders exactly one at a time. */
sealed interface ChangePasswordStep {
    /** The system prompt (screen lock or strong biometric) gates the change. */
    data object DeviceAuthentication : ChangePasswordStep

    /**
     * The current master password gates the change: no screen lock, or the prompt was cancelled
     * or failed.
     *
     * @property isIncorrect The last entry did not unwrap the vault.
     * @property isVerifying A check is in flight; the dialog ignores another submit.
     */
    data class CurrentPassword(
        val isIncorrect: Boolean = false,
        val isVerifying: Boolean = false,
    ) : ChangePasswordStep

    /** Gate passed: `UpdatePasswordDialog`. */
    data object NewPassword : ChangePasswordStep
}

/** Outcome of the re-wrap, shown once as a toast. */
enum class PasswordChangeResult {
    SUCCESS,
    FAILURE,
}

/**
 * How the change-password gate was passed.
 *
 * @param wireName Value of the `gate` analytics param; append-only once shipped.
 */
enum class ChangePasswordGate(val wireName: String) {
    DEVICE_AUTH("device_auth"),
    MASTER_PASSWORD("master_password"),
}
