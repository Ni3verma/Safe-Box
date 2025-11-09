package com.andryoga.composeapp.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.composeapp.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.composeapp.common.CommonConstants.BACKUP_PARAM_PASSWORD
import com.andryoga.composeapp.common.CommonConstants.RESTORE_PARAM_FILE_URI
import com.andryoga.composeapp.common.CommonConstants.RESTORE_PARAM_PASSWORD
import com.andryoga.composeapp.common.CommonConstants.WORKER_NAME_BACKUP_DATA
import com.andryoga.composeapp.common.CommonConstants.WORKER_NAME_RESTORE_DATA
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import com.andryoga.composeapp.worker.BackupDataWorker
import com.andryoga.composeapp.worker.RestoreDataWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NewBackupOrRestoreVM @Inject constructor(
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : ViewModel() {
    private lateinit var operation: Operation
    private val _uiState =
        MutableStateFlow(NewBackupOrRestoreScreenState())
    val uiState: StateFlow<NewBackupOrRestoreScreenState> = _uiState

    fun initVM(operation: Operation) {
        this.operation = operation
        updateWorkflowState(WorkflowState.ASK_FOR_PASSWORD)
    }

    fun onScreenAction(action: ScreenAction) {
        when (action) {
            is ScreenAction.ConfirmPasswordRequest -> handleConfirmPasswordRequest(action.password)
            ScreenAction.Dismiss -> TODO()
        }
    }

    private fun handleConfirmPasswordRequest(password: String) {
        viewModelScope.launch {
            val isPswrdCorrect = userDetailsRepository.checkPassword(password)
            if (isPswrdCorrect.not()) {
                updateWorkflowState(WorkflowState.WRONG_PASSWORD)
            } else {
                Timber.i("pswrd is correct, preparing work req")
                val requestId = createWorkRequest(password, operation)
                Timber.i("enqueued backup work")
                workManager.getWorkInfoByIdFlow(requestId).onEach { workInfo ->
                    Timber.i("backup work state: ${workInfo?.state}")
                    when (workInfo?.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> updateWorkflowState(
                            WorkflowState.IN_PROGRESS
                        )

                        WorkInfo.State.SUCCEEDED -> updateWorkflowState(
                            WorkflowState.SUCCESS
                        )

                        WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED, null -> updateWorkflowState(
                            WorkflowState.FAILED
                        )

                    }
                }.launchIn(viewModelScope)
            }
        }
    }

    private fun createWorkRequest(password: String, operation: Operation): UUID {
        return when (operation) {
            Operation.Backup -> createBackupWorkRequest(password)
            is Operation.Restore -> createRestoreWorkRequest(
                password,
                operation.fileUri.toString().orEmpty()
            )
        }
    }

    private fun createBackupWorkRequest(password: String): UUID {
        val backupDataRequest = OneTimeWorkRequestBuilder<BackupDataWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BACKUP_PARAM_PASSWORD, symmetricKeyUtils.encrypt(password))
                    .putBoolean(BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, true)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            WORKER_NAME_BACKUP_DATA,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            backupDataRequest
        )

        return backupDataRequest.id
    }

    private fun createRestoreWorkRequest(password: String, fileUri: String): UUID {
        val restoreDataRequest = OneTimeWorkRequestBuilder<RestoreDataWorker>()
            .setInputData(
                Data.Builder()
                    .putString(RESTORE_PARAM_PASSWORD, symmetricKeyUtils.encrypt(password))
                    .putString(RESTORE_PARAM_FILE_URI, fileUri)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            WORKER_NAME_RESTORE_DATA,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            restoreDataRequest
        )

        return restoreDataRequest.id
    }

    private fun updateWorkflowState(workflowState: WorkflowState) {
        _uiState.update { currentState ->
            currentState.copy(
                workflowState = workflowState
            )
        }
    }
}