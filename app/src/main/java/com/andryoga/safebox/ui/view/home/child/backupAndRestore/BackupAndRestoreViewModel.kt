package com.andryoga.safebox.ui.view.home.child.backupAndRestore

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class BackupAndRestoreViewModel @Inject constructor(
    private val backupMetadataRepository: BackupMetadataRepository,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {

    val backupMetadata = backupMetadataRepository.getBackupMetadata()

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

    fun checkUserPassword(password: String) {
        viewModelScope.launch {
            _isPasswordCorrect.value = userDetailsRepository.checkPassword(password)
        }
    }
}
