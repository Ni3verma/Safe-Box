package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreViewModel @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository
) : ViewModel() {
    val deleteItLater = viewModelScope.launch {
        backupMetadataRepository.isBackupPathSet()
    }
}
