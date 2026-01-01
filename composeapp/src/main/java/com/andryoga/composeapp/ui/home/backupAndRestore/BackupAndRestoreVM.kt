package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.ui.core.ActiveSessionManager
import com.andryoga.composeapp.ui.home.navigation.HomeRouteType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreVM @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val activeSessionManager: ActiveSessionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenState())
    val uiState: StateFlow<ScreenState> = _uiState

    private val _startRestoreWorkflow = MutableStateFlow(false)

    /**
     * State is made true when the screen needs to started with start restore workflow.
     * i.e. open file system screen where user chooses the backup file to restore.
     * This param come as part of navigation param*/
    val startRestoreWorkflow = _startRestoreWorkflow.asStateFlow()

    init {
        val args: HomeRouteType.BackupAndRestoreRoute =
            savedStateHandle.toRoute<HomeRouteType.BackupAndRestoreRoute>()
        if (args.startWithRestoreWorkflow) {
            _startRestoreWorkflow.value = true
        }

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

            is ScreenAction.RestoreFileSelected -> {
                if (action.uri != null) {
                    updateNewBackupOrRestoreScreenState(
                        newState = NewBackupOrRestoreScreenState.StartedForRestore(
                            fileUri = action.uri
                        )
                    )
                }
            }
        }
    }

    fun pauseActiveSessionManager() {
        activeSessionManager.setPaused(true)
    }

    fun resumeActiveSessionManager() {
        activeSessionManager.setPaused(false)
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