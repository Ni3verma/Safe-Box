package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreVM @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenState())
    val uiState: StateFlow<ScreenState> = _uiState

    init {
        backupMetadataRepository.getBackupMetadata().onEach { backupMetadata ->
            _uiState.update { currentState ->
                currentState.copy(
                    backupState = if (backupMetadata == null) {
                        BackupPathNotSet()
                    } else {
                        BackupPathSet(
                            backupPath = backupMetadata.path,
                            backupTime = backupMetadata.lastBackupTime
                        )
                    }
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onScreenAction(action: ScreenAction) {
        when (action) {
            is ScreenAction.BackupPathSelected -> handleBackupPathSelected(action.uri)
            ScreenAction.NewBackupClick -> updateNewBackupOrRestoreScreenState(
                newState = NewBackupOrRestoreScreenState.StartedForBackup
            )

            ScreenAction.NewBackupOrRestoreDismiss -> updateNewBackupOrRestoreScreenState(
                newState = NewBackupOrRestoreScreenState.NotStarted
            )

            is ScreenAction.RestoreFileSelected -> updateNewBackupOrRestoreScreenState(
                newState = NewBackupOrRestoreScreenState.StartedForRestore(
                    fileUri = action.uri
                )
            )
        }
    }
    private fun handleBackupPathSelected(uri: Uri?) {
        viewModelScope.launch {
            backupMetadataRepository.insertBackupMetadata(uriPath = uri)
        }
    }

    private fun updateNewBackupOrRestoreScreenState(newState: NewBackupOrRestoreScreenState) {
        _uiState.update { currentState ->
            currentState.copy(
                newBackupOrRestoreScreenState = newState
            )
        }
    }
}