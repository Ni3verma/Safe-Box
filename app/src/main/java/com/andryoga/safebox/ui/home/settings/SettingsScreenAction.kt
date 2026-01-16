package com.andryoga.safebox.ui.home.settings

sealed interface SettingsScreenAction {
    class UpdatePrivacy(val enabled: Boolean) : SettingsScreenAction
    class UpdateAutoBackupAfterLogin(val count: Boolean) : SettingsScreenAction
    class UpdateAwayTimeout(val timeout: Int) : SettingsScreenAction
    class UpdatePasswordAfterXBiometric(val limit: Int) : SettingsScreenAction
    object SendFeedback : SettingsScreenAction
    object ReviewApp : SettingsScreenAction
    object OpenGithubProject : SettingsScreenAction

}