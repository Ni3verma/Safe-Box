package com.andryoga.safebox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.ui.loading.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    backupMetadataRepository: BackupMetadataRepository,
    settingsDataStore: SettingsDataStore,
    vaultRepository: VaultRepository,
    sessionKeyHolder: SessionKeyHolder,
) : ViewModel() {
    /**
     * Whether the vault session is open. Home's gate collects it: a back stack restored after
     * process death or a navigation that skipped Login finds it `false` on entry, and a clear while
     * Home is showing turns it `false` the same moment, so Home leaves for Login either way.
     */
    val isVaultUnlocked: StateFlow<Boolean> = sessionKeyHolder.isUnlocked

    val isBackupPathSet: StateFlow<Boolean> = backupMetadataRepository.getBackupMetadata().map {
        it != null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = true
    )

    /** Where the app starts: Room decides ([VaultRepository.hasVault]), never a preference. */
    val loadingState: StateFlow<LoadingState> = flow {
        emit(
            if (vaultRepository.hasVault()) {
                LoadingState.ProceedToLogin
            } else {
                LoadingState.ProceedToSignup
            },
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
