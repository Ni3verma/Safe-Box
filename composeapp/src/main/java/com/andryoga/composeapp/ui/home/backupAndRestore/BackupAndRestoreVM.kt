package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.composeapp.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.composeapp.common.CommonConstants.BACKUP_PARAM_PASSWORD
import com.andryoga.composeapp.common.CommonConstants.WORKER_NAME_BACKUP_DATA
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import com.andryoga.composeapp.worker.BackupDataWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
            ScreenAction.NewBackupClick -> updateNewBackupState(newBackupState = NewBackupState.ASK_FOR_PASSWORD)
            is ScreenAction.BackupPathSelected -> handleBackupPathSelected(action.uri)
            ScreenAction.EditBackupPathClick -> TODO()
            ScreenAction.RestoreClick -> TODO()
            ScreenAction.NewBackupCancel -> updateNewBackupState(newBackupState = NewBackupState.NOT_STARTED)
            is ScreenAction.NewBackupRequest -> handleNewBackupRequest(action.password)
        }
    }

    private fun handleNewBackupRequest(password: String) {
        viewModelScope.launch {
            updateNewBackupState(NewBackupState.VALIDATING_PASSWORD)
            val isPswrdCorrect = userDetailsRepository.checkPassword(password)
            if (isPswrdCorrect.not()) {
                updateNewBackupState(NewBackupState.WRONG_PASSWORD)
            } else {
                Timber.i("pswrd is correct, preparing backup work")
                val backupDataRequest = OneTimeWorkRequestBuilder<BackupDataWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(BACKUP_PARAM_PASSWORD, symmetricKeyUtils.encrypt(password))
                            .putBoolean(BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, true).build()
                    )
                    .build()

                workManager.enqueueUniqueWork(
                    WORKER_NAME_BACKUP_DATA,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    backupDataRequest
                )
                Timber.i("enqueued backup work")
                workManager.getWorkInfoByIdFlow(backupDataRequest.id).onEach { workInfo ->
                    Timber.i("backup work state: ${workInfo?.state}")
                    when (workInfo?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> updateNewBackupState(
                            NewBackupState.IN_PROGRESS
                        )

                        WorkInfo.State.SUCCEEDED -> updateNewBackupState(
                            NewBackupState.SUCCESS
                        )

                        WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED, null -> updateNewBackupState(
                            NewBackupState.FAILED
                        )

                    }
                }.launchIn(viewModelScope)
            }
        }
    }

    private fun handleBackupPathSelected(uri: Uri?) {
        viewModelScope.launch {
            backupMetadataRepository.insertBackupMetadata(uriPath = uri)
        }
    }

    private fun updateNewBackupState(newBackupState: NewBackupState) {
        _uiState.update { currentState ->
            currentState.copy(
                newBackupState = newBackupState
            )
        }
    }
}