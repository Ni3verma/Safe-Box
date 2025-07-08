package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_PASSWORD
import com.andryoga.safebox.common.CommonConstants.RESTORE_PARAM_FILE_URI
import com.andryoga.safebox.common.CommonConstants.RESTORE_PARAM_PASSWORD
import com.andryoga.safebox.common.CommonConstants.WORKER_NAME_BACKUP_DATA
import com.andryoga.safebox.common.CommonConstants.WORKER_NAME_RESTORE_DATA
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.worker.BackupDataWorker
import com.andryoga.safebox.worker.RestoreDataWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreViewModel
@Inject
constructor(
    private val backupMetadataRepository: BackupMetadataRepository,
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils,
) : ViewModel() {
    val backupMetadata =
        flow {
            emit(Resource.loading(null))
            backupMetadataRepository.getBackupMetadata().collect {
                emit(Resource.success(it))
                _backupScreenState.value = BackupScreenState.INITIAL_STATE
            }
        }

    private val _backupScreenState =
        MutableStateFlow(BackupScreenState.INITIAL_STATE)
    val backupScreenState: StateFlow<BackupScreenState> = _backupScreenState

    private val _restoreScreenState =
        MutableStateFlow(RestoreScreenState.INITIAL_STATE)
    val restoreScreenState: StateFlow<RestoreScreenState> = _restoreScreenState

    private val _restoreWorkEnqueued = MutableStateFlow<UUID?>(null)
    val restoreWorkEnqueued: StateFlow<UUID?> = _restoreWorkEnqueued

    lateinit var selectedFileUriForRestore: String

    fun setBackupMetadata(uri: Uri) {
        viewModelScope.launch {
            Timber.i("adding backup metadata in db")
            val backupMetadataEntity =
                BackupMetadataEntity(
                    1,
                    uri.toString(),
                    uri.path!!,
                    null,
                    Date(),
                )
            backupMetadataRepository.insertBackupMetadata(backupMetadataEntity)
        }
    }

    @ExperimentalCoroutinesApi
    fun backupData(password: String) {
        viewModelScope.launch {
            val isPswrdCorrect = userDetailsRepository.checkPassword(password)
            if (isPswrdCorrect) {
                _backupScreenState.value = BackupScreenState.IN_PROGRESS
                Timber.i("pswrd is correct, preparing backup work")
                val backupDataRequest =
                    OneTimeWorkRequestBuilder<BackupDataWorker>()
                        .setInputData(
                            Data(
                                mapOf(
                                    BACKUP_PARAM_PASSWORD to symmetricKeyUtils.encrypt(password),
                                    BACKUP_PARAM_IS_SHOW_START_NOTIFICATION to true,
                                ),
                            ),
                        )
                        .build()
                workManager.enqueueUniqueWork(
                    WORKER_NAME_BACKUP_DATA,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    backupDataRequest,
                )
                Timber.i("enqueued backup work")
            } else {
                _backupScreenState.value = BackupScreenState.WRONG_PASSWORD
                Timber.i("wrong pswrd entered")
            }
        }
    }

    @ExperimentalCoroutinesApi
    fun restoreData(password: String) {
        viewModelScope.launch {
            _restoreScreenState.value = RestoreScreenState.IN_PROGRESS
            Timber.i("preparing restore work")
            val restoreDataRequest =
                OneTimeWorkRequestBuilder<RestoreDataWorker>()
                    .setInputData(
                        Data(
                            mapOf(
                                RESTORE_PARAM_PASSWORD to symmetricKeyUtils.encrypt(password),
                                RESTORE_PARAM_FILE_URI to selectedFileUriForRestore,
                            ),
                        ),
                    )
                    .build()
            workManager.enqueueUniqueWork(
                WORKER_NAME_RESTORE_DATA,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                restoreDataRequest,
            )
            _restoreWorkEnqueued.value = restoreDataRequest.id
            Timber.i("enqueued restore work")
        }
    }

    fun setBackupScreenState(newState: BackupScreenState) {
        _backupScreenState.value = newState
    }

    fun setRestoreScreenState(newState: RestoreScreenState) {
        _restoreScreenState.value = newState
    }
}
