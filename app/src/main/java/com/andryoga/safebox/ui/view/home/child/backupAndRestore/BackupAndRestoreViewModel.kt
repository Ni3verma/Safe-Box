package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.andryoga.safebox.common.Constants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.safebox.common.Constants.BACKUP_PARAM_PASSWORD
import com.andryoga.safebox.common.Constants.BACKUP_WORK_NAME
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.Resource
import com.andryoga.safebox.worker.BackupDataWorker
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
class BackupAndRestoreViewModel @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository,
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : ViewModel() {

    val backupMetadata = flow {
        emit(Resource.loading(null))
        backupMetadataRepository.getBackupMetadata().collect {
            emit(Resource.success(it))
        }
    }

    private val _isPasswordCorrect = MutableStateFlow<Boolean?>(null)
    val isPasswordCorrect: StateFlow<Boolean?> = _isPasswordCorrect

    fun setBackupMetadata(uri: Uri) {
        viewModelScope.launch {
            Timber.i("adding backup metadata in db")
            val backupMetadataEntity = BackupMetadataEntity(
                1, uri.toString(), uri.path!!, null,
                Date()
            )
            backupMetadataRepository.insertBackupMetadata(backupMetadataEntity)
        }
    }

    @ExperimentalCoroutinesApi
    fun backupData(password: String) {
        viewModelScope.launch {
            val isPswrdCorrect = userDetailsRepository.checkPassword(password)
            _isPasswordCorrect.value = isPswrdCorrect
            if (isPswrdCorrect) {
                Timber.i("pswrd is correct, preparing backup work")
                val backupDataRequest = OneTimeWorkRequestBuilder<BackupDataWorker>()
                    .setInputData(
                        Data(
                            mapOf(
                                BACKUP_PARAM_PASSWORD to symmetricKeyUtils.encrypt(password),
                                BACKUP_PARAM_IS_SHOW_START_NOTIFICATION to true
                            )
                        )
                    )
                    .build()
                workManager.enqueueUniqueWork(
                    BACKUP_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    backupDataRequest
                )
                Timber.i("enqueued backup work")
            } else {
                Timber.i("wrong pswrd entered")
            }
        }
    }
}
