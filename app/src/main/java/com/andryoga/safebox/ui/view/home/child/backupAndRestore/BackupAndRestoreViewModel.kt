package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import androidx.lifecycle.ViewModel
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreViewModel @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository
) : ViewModel() {

    val backupMetadata = backupMetadataRepository.getBackupMetadata()
}
