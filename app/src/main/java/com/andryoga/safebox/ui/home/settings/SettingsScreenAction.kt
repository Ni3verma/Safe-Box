package com.andryoga.safebox.ui.home.settings

sealed interface SettingsScreenAction {
    // when privacy mode toggle is updated
    class UpdatePrivacy(val enabled: Boolean) : SettingsScreenAction

    // when auto-backup on login toggle is updated
    class UpdateAutoBackupAfterLogin(val enabled: Boolean) : SettingsScreenAction

    // when auto-lock away timeout slider is updated
    class UpdateAwayTimeout(val timeout: Int) : SettingsScreenAction

    // when consecutive biometric login limit slider is updated
    class UpdatePasswordAfterXBiometric(val limit: Int) : SettingsScreenAction

    // when user clicks send feedback
    object SendFeedback : SettingsScreenAction

    // when user clicks review app on play store
    object ReviewApp : SettingsScreenAction

    // when user clicks contribute on github
    object OpenGithubProject : SettingsScreenAction

    // when user submits new master password and hint after device security auth
    class OnUpdateMasterPassword(val newPassword: String, val hint: String) : SettingsScreenAction

    // when device security required dialog is displayed
    object OnDeviceSecurityRequiredDialogShown : SettingsScreenAction

    // when user clicks open settings in device security required dialog
    object OnDeviceSecurityRequiredOpenSettingsClicked : SettingsScreenAction

    // when user dismisses/cancels device security required dialog
    object OnDeviceSecurityRequiredDismissClicked : SettingsScreenAction

    // when update password dialog is displayed
    object OnUpdatePasswordDialogShown : SettingsScreenAction

    // when user dismisses/cancels update password dialog
    object OnUpdatePasswordDismissClicked : SettingsScreenAction
}