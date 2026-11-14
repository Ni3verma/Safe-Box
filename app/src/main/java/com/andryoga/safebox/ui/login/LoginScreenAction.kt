package com.andryoga.safebox.ui.login

import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import javax.crypto.Cipher

sealed interface LoginScreenAction {
    // when user clicks show hint to reveal their saved password hint
    object ShowHintClicked : LoginScreenAction

    // when user clicks login with entered master password
    class LoginClicked(val password: String) : LoginScreenAction

    /**
     * The fingerprint prompt succeeded. [cipher] is the authorised cipher of the `CryptoObject`
     * the screen bound from [LoginUiState.biometricUnlockCipher]; `null` means the prompt was not
     * crypto-bound, which the ViewModel refuses to unlock on.
     */
    class BiometricSuccess(val cipher: Cipher?) : LoginScreenAction

    // when biometric authentication fails or is cancelled for login unlock
    object BiometricError : LoginScreenAction

    /**
     * device supports biometric auth, run business logic to determine if biometric auth should be triggered
     */
    object BiometricAvailable : LoginScreenAction

    // when user submits new password and hint to reset master password after device security auth
    class OnResetPassword(val newPassword: String, val hint: String) : LoginScreenAction

    // when device security required dialog is displayed
    object OnDeviceSecurityRequiredDialogShown : LoginScreenAction

    // when user clicks open settings in device security required dialog
    object OnDeviceSecurityRequiredOpenSettingsClicked : LoginScreenAction

    // when launching system security settings fails from device security required dialog
    object OnDeviceSecurityRequiredOpenSettingsFailed : LoginScreenAction

    // when user dismisses/cancels device security required dialog
    object OnDeviceSecurityRequiredDismissClicked : LoginScreenAction

    // when update password dialog is displayed
    object OnUpdatePasswordDialogShown : LoginScreenAction

    // when user dismisses/cancels update password dialog
    object OnUpdatePasswordDismissClicked : LoginScreenAction

    // when user taps Forgot password; the ViewModel decides which step follows from the vault state
    object ForgotPasswordClicked : LoginScreenAction

    // legacy reset: today's gate (no CryptoObject) accepted the credential
    // TODO(D3): legacy vault (crypto_version = 1) only
    object OnLegacyResetAuthenticated : LoginScreenAction

    /**
     * v2 recovery: the device prompt succeeded. [cipher] is the authorised cipher of the bound
     * `CryptoObject` (API 30+), or `null` when none was bound (API 29, time-bound key).
     */
    class OnRecoveryAuthenticated(val cipher: Cipher?) : LoginScreenAction

    // the forgot-password prompt (legacy or v2) errored or was cancelled
    object OnForgotPasswordAuthCancelled : LoginScreenAction

    // Back on the recovery-not-set-up card
    object OnRecoveryNotSetUpDismissed : LoginScreenAction

    // when the erase-vault dialog is displayed
    object OnEraseVaultDialogShown : LoginScreenAction

    // when user confirms the erase-vault dialog
    object OnEraseVaultConfirmClicked : LoginScreenAction

    // when user dismisses/cancels the erase-vault dialog
    object OnEraseVaultCancelClicked : LoginScreenAction

    // when the migration failure card is shown on the login screen
    // TODO(D3): legacy vault (crypto_version = 1) only
    class OnMigrationFailureShown(val reason: MigrationFailureReason) : LoginScreenAction

    // when the user taps Try again on the migration failure card
    // TODO(D3): legacy vault (crypto_version = 1) only
    object OnMigrationRetryClicked : LoginScreenAction

    // when the user taps Continue for now on the migration failure card to enter a legacy session
    // TODO(D3): legacy vault (crypto_version = 1) only
    object OnMigrationContinueLegacyClicked : LoginScreenAction
}