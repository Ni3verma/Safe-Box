package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreViewModel @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository
) : ViewModel() {

    val backupMetadata = backupMetadataRepository.getBackupMetadata()

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
}
