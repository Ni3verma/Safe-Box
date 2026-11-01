package com.andryoga.safebox.ui.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.ui.core.ActiveSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(

    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val userDetailsRepository: UserDetailsRepository,
    private val activeSessionManager: ActiveSessionManager,
) : ViewModel() {

    val uiState: StateFlow<Settings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Settings()
        )

    fun onScreenAction(action: SettingsScreenAction) {
        when (action) {
            is SettingsScreenAction.UpdatePrivacy -> updatePrivacy(action.enabled)
            is SettingsScreenAction.UpdateAutoBackupAfterLogin -> updateAutoBackupAfterPasswordLogin(
                action.enabled
            )

            is SettingsScreenAction.UpdateAwayTimeout -> updateAwayTimeout(action.timeout)
            is SettingsScreenAction.UpdatePasswordAfterXBiometric -> updatePasswordAfterXBiometricLogin(
                action.limit
            )

            is SettingsScreenAction.OnUpdateMasterPassword -> updateMasterPassword(
                action.newPassword,
                action.hint
            )

            SettingsScreenAction.OpenGithubProject -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_GITHUB)
            }

            SettingsScreenAction.OpenBuyMeACoffee -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_BUY_ME_A_COFFEE)
            }

            SettingsScreenAction.ReviewApp -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_PLAY_STORE)
            }

            SettingsScreenAction.SendFeedback -> {
                analyticsHelper.logEvent(AnalyticsKey.EMAIL_FEEDBACK)
            }

            SettingsScreenAction.OnDeviceSecurityRequiredDialogShown -> {
                logFromSettingsScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
            }

            SettingsScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked -> {
                activeSessionManager.setPaused(true)
                logFromSettingsScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }

            SettingsScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed -> {
                logFromSettingsScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
            }

            SettingsScreenAction.OnDeviceSecurityRequiredDismissClicked -> {
                logFromSettingsScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }

            SettingsScreenAction.OnUpdatePasswordDialogShown -> {
                logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            SettingsScreenAction.OnUpdatePasswordDismissClicked -> {
                logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
            }
        }
    }

    /** The same dialog events are raised from the login screen; `source` keeps the two apart. */
    private fun logFromSettingsScreen(key: AnalyticsKey) {
        analyticsHelper.logEvent(key) {
            param(AnalyticsParam.SOURCE, AnalyticsSource.SETTINGS.value)
        }
    }

    private fun updatePrivacy(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updatePrivacy(enabled)
    }

    private fun updateAwayTimeout(value: Int) = viewModelScope.launch {
        settingsDataStore.updateAwayTimeout(value)
    }

    private fun updateAutoBackupAfterPasswordLogin(value: Boolean) =
        viewModelScope.launch {
            settingsDataStore.updateAutoBackupAfterPasswordLogin(value)
        }

    private fun updatePasswordAfterXBiometricLogin(value: Int) =
        viewModelScope.launch {
            settingsDataStore.updatePasswordAfterXBiometricLogin(value)
        }

    private fun updateMasterPassword(newPassword: String, hint: String) =
        viewModelScope.launch {
            Timber.i("updating master password and hint from settings screen")
            logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
            userDetailsRepository.updatePasswordAndHint(newPassword, hint)
            Timber.i("master password and hint successfully updated from settings screen")
        }
}