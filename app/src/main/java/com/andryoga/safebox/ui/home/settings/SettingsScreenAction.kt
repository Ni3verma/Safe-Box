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

    // when the fingerprint-unlock switch is toggled
    class UpdateBiometricUnlock(val enabled: Boolean) : SettingsScreenAction

    // when the screen resumes; enrolment may have changed while the user was away
    object OnScreenResumed : SettingsScreenAction

    // when user clicks send feedback
    object SendFeedback : SettingsScreenAction

    // when user clicks review app on play store
    object ReviewApp : SettingsScreenAction

    // when user clicks contribute on github
    object OpenGithubProject : SettingsScreenAction

    // when user clicks buy me a coffee
    object OpenBuyMeACoffee : SettingsScreenAction

    // when user taps change master password; the screen knows whether the prompt can be shown
    class ChangePasswordClicked(val canUseDeviceAuth: Boolean) : SettingsScreenAction

    // when the change-password device prompt succeeds
    object OnDeviceAuthSucceeded : SettingsScreenAction

    // when the change-password device prompt is cancelled or fails
    object OnDeviceAuthFailedOrCancelled : SettingsScreenAction

    // when user submits the current master password in the change-password gate
    class OnCurrentPasswordSubmitted(val password: String) : SettingsScreenAction

    // when user cancels the current master password dialog
    object OnCurrentPasswordDismissed : SettingsScreenAction

    // when user submits new master password and hint after passing the gate
    class OnUpdateMasterPassword(val newPassword: String, val hint: String) : SettingsScreenAction

    // when update password dialog is displayed
    object OnUpdatePasswordDialogShown : SettingsScreenAction

    // when user dismisses/cancels update password dialog
    object OnUpdatePasswordDismissClicked : SettingsScreenAction

    // when the password change toast has been shown
    object OnPasswordChangeResultShown : SettingsScreenAction
}