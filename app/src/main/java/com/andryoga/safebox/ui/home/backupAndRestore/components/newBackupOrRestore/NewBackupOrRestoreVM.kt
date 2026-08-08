package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.di.IsDebug
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.andryoga.safebox.worker.BackupDataWorker
import com.andryoga.safebox.worker.RestoreDataWorker
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NewBackupOrRestoreVM @Inject constructor(
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val analyticsHelper: AnalyticsHelper,
    val inAppReviewManager: Lazy<InAppReviewManager>,
    @param:IsDebug private val isDebug: Boolean
) : ViewModel() {
    private lateinit var operation: Operation
    private val _uiState =
        MutableStateFlow(NewBackupOrRestoreScreenState())
    val uiState: StateFlow<NewBackupOrRestoreScreenState> = _uiState

    init {
        if (isDebug) {
            _uiState.update { it.copy(defaultPassword = "Qwerty@@135") }
        }
    }

    private val _startReviewOnRestoreSuccess = Channel<Unit>(Channel.CONFLATED)

    /**
     * Start In-App review flow if restore was success.
     * */
    val startReviewOnRestoreSuccess = _startReviewOnRestoreSuccess.receiveAsFlow()

    fun initVM(operation: Operation) {
        this.operation = operation
        updateWorkflowState(WorkflowState.ASK_FOR_PASSWORD)
    }

    fun onScreenAction(action: ScreenAction) {
        when (action) {
            is ScreenAction.PasswordConfirmed -> handlePasswordConfirmedAction(action.password)
        }
    }

    private fun handlePasswordConfirmedAction(password: String) {
        viewModelScope.launch {
            when (operation) {
                Operation.Backup -> {
                    Timber.i("password confirmed for new backup")
                    analyticsHelper.logEvent(AnalyticsKey.BACKUP_STARTED)
                }

                is Operation.Restore -> {
                    Timber.i("password confirmed for new restore")
                    analyticsHelper.logEvent(AnalyticsKey.RESTORE_STARTED)
                }
            }

            Timber.i("enqueuing work req")
            val requestId = enqueueWorkRequest(password, operation)
            workManager.getWorkInfoByIdFlow(requestId).onEach { workInfo ->
                Timber.i("backup/restore work state: ${workInfo?.state}")
                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> updateWorkflowState(
                        WorkflowState.IN_PROGRESS
                    )

                    WorkInfo.State.SUCCEEDED -> {
                        if (operation is Operation.Restore) {
                            _startReviewOnRestoreSuccess.send(Unit)
                        }
                        updateWorkflowState(
                            WorkflowState.SUCCESS
                        )
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED, null -> {
                        val targetState =
                            if (operation is Operation.Restore && workInfo?.state == WorkInfo.State.FAILED) {
                                when (RestoreFailureReason.fromWorkData(workInfo.outputData)) {
                                    RestoreFailureReason.INCORRECT_PASSWORD -> WorkflowState.WRONG_PASSWORD
                                    RestoreFailureReason.CORRUPT_OR_INVALID_FILE -> WorkflowState.CORRUPT_FILE
                                    RestoreFailureReason.UNKNOWN_ERROR -> WorkflowState.FAILED
                                }
                            } else {
                                WorkflowState.FAILED
                            }
                        updateWorkflowState(targetState)
                    }

                }
            }.launchIn(viewModelScope)
        }
    }

    private fun enqueueWorkRequest(password: String, operation: Operation): UUID {
        return when (operation) {
            Operation.Backup -> enqueueBackupWork(password)
            is Operation.Restore -> enqueueRestoreWork(
                password,
                operation.fileUri.toString()
            )
        }
    }

    private fun enqueueBackupWork(password: String): UUID {
        return BackupDataWorker.enqueueRequest(
            password = password,
            showBackupStartNotification = true,
            workManager = workManager,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    private fun enqueueRestoreWork(password: String, fileUri: String): UUID {
        val restoreDataRequest = OneTimeWorkRequestBuilder<RestoreDataWorker>()
            .setInputData(
                Data.Builder()
                    .putString(
                        CommonConstants.RESTORE_PARAM_PASSWORD,
                        symmetricKeyUtils.encrypt(password)
                    )
                    .putString(CommonConstants.RESTORE_PARAM_FILE_URI, fileUri)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            CommonConstants.WORKER_NAME_RESTORE_DATA,
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