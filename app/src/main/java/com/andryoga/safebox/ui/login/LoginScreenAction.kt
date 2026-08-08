package com.andryoga.safebox.ui.login

sealed interface LoginScreenAction {
    // when user clicks show hint to reveal their saved password hint
    object ShowHintClicked : LoginScreenAction

    // when user clicks login with entered master password
    class LoginClicked(val password: String) : LoginScreenAction

    // when biometric authentication completes successfully for login unlock
    object BiometricSuccess : LoginScreenAction

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
}