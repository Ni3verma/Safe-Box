package com.andryoga.composeapp.data.repository.interfaces

import android.net.Uri
import com.andryoga.composeapp.domain.models.backup.BackupPathData
import kotlinx.coroutines.flow.Flow

interface BackupMetadataRepository {
    suspend fun insertBackupMetadata(uriPath: Uri?)
    suspend fun deleteBackupMetadata()
    suspend fun updateLastBackupDate(date: Long)
    suspend fun isBackupPathSet(): Boolean
    fun getBackupMetadata(): Flow<BackupPathData?>
}
