package com.andryoga.safebox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.loading.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MainViewModel @Inject constructor(
    backupMetadataRepository: BackupMetadataRepository,
    settingsDataStore: SettingsDataStore,
    activeSessionManager: ActiveSessionManager,
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
) : ViewModel() {
    val isBackupPathSet: StateFlow<Boolean> = backupMetadataRepository.getBackupMetadata().map {
        it != null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = true
    )

    val logoutEvent: SharedFlow<Unit> = activeSessionManager.logoutEvent
        .onEach {
            Timber.i("received logout event")
        }
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds),
            replay = 0
        )

    val loadingState: StateFlow<LoadingState> = flow {
        val isSignupRequired =
            encryptedPreferenceProvider.getBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                true
            )
        emit(
            if (isSignupRequired) {
                LoadingState.ProceedToSignup
            } else {
                LoadingState.ProceedToLogin
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoadingState.Initial
    )

    val isPrivacyEnabled: StateFlow<Boolean> = settingsDataStore.isPrivacyEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )
}