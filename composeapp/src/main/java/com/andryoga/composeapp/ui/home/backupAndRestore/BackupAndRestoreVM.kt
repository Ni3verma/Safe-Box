package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
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
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenState())
    val uiState: StateFlow<ScreenState> = _uiState

    init {
        backupMetadataRepository.getBackupMetadata().onEach { backupMetadata ->
            _uiState.update { currentState ->
                currentState.copy(
                    backupState = if (backupMetadata == null) {
                        BackupNotSet()
                    } else {
                        BackupSet(
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
            ScreenAction.BackupClick -> TODO()
            is ScreenAction.BackupPathSelected -> handleBackupPathSelected(action.uri)
            ScreenAction.EditBackupPathClick -> TODO()
            ScreenAction.RestoreClick -> TODO()
        }
    }

    private fun handleBackupPathSelected(uri: Uri?) {
        viewModelScope.launch {
            backupMetadataRepository.insertBackupMetadata(uriPath = uri)
        }
    }
}