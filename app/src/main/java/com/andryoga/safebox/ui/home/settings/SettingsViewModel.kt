package com.andryoga.safebox.ui.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val dispatchers: DispatchersProvider
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

            SettingsScreenAction.OpenGithubProject -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_GITHUB)
            }

            SettingsScreenAction.ReviewApp -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_PLAY_STORE)
            }

            SettingsScreenAction.SendFeedback -> {
                analyticsHelper.logEvent(AnalyticsKey.EMAIL_FEEDBACK)
            }
        }
    }

    private fun updatePrivacy(enabled: Boolean) = viewModelScope.launch(dispatchers.io) {
        settingsDataStore.updatePrivacy(enabled)
    }

    private fun updateAwayTimeout(value: Int) = viewModelScope.launch(dispatchers.io) {
        settingsDataStore.updateAwayTimeout(value)
    }

    private fun updateAutoBackupAfterPasswordLogin(value: Boolean) =
        viewModelScope.launch(dispatchers.io) {
        settingsDataStore.updateAutoBackupAfterPasswordLogin(value)
    }

    private fun updatePasswordAfterXBiometricLogin(value: Int) =
        viewModelScope.launch(dispatchers.io) {
        settingsDataStore.updatePasswordAfterXBiometricLogin(value)
    }
}