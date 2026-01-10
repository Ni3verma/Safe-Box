package com.andryoga.safebox.ui.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.AnalyticsKeys
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
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
                action.count
            )

            is SettingsScreenAction.UpdateAwayTimeout -> updateAwayTimeout(action.timeout)
            is SettingsScreenAction.UpdatePasswordAfterXBiometric -> updatePasswordAfterXBiometricLogin(
                action.limit
            )

            SettingsScreenAction.OpenGithubProject -> {
                Firebase.analytics.logEvent(AnalyticsKeys.OPEN_GITHUB, null)
            }

            SettingsScreenAction.ReviewApp -> {
                Firebase.analytics.logEvent(AnalyticsKeys.OPEN_PLAY_STORE, null)
            }

            SettingsScreenAction.SendFeedback -> {
                Firebase.analytics.logEvent(AnalyticsKeys.EMAIL_FEEDBACK, null)
            }
        }
    }

    private fun updatePrivacy(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updatePrivacy(enabled)
    }

    private fun updateAwayTimeout(value: Int) = viewModelScope.launch {
        settingsDataStore.updateAwayTimeout(value)
    }

    private fun updateAutoBackupAfterPasswordLogin(value: Boolean) = viewModelScope.launch {
        settingsDataStore.updateAutoBackupAfterPasswordLogin(value)
    }

    private fun updatePasswordAfterXBiometricLogin(value: Int) = viewModelScope.launch {
        settingsDataStore.updatePasswordAfterXBiometricLogin(value)
    }
}