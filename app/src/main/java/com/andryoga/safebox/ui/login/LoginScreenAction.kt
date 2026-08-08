package com.andryoga.safebox.ui.login

sealed interface LoginScreenAction {
    object ShowHintClicked : LoginScreenAction
    class LoginClicked(val password: String) : LoginScreenAction
    object BiometricSuccess : LoginScreenAction
    object BiometricError : LoginScreenAction

    /**
     * device supports biometric auth, run business logic to determine if biometric auth should be triggered
     * */
    object BiometricAvailable : LoginScreenAction
    class OnResetPassword(val newPassword: String, val hint: String) : LoginScreenAction
    object OnDeviceSecurityRequiredDialogShown : LoginScreenAction
    object OnDeviceSecurityRequiredOpenSettingsClicked : LoginScreenAction
    object OnDeviceSecurityRequiredDismissClicked : LoginScreenAction
    object OnUpdatePasswordDialogShown : LoginScreenAction
    object OnUpdatePasswordDismissClicked : LoginScreenAction
}