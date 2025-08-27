package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.data.db.entity.BackupMetadataEntity
import kotlinx.coroutines.flow.Flow

interface BackupMetadataRepository {
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)
    suspend fun deleteBackupMetadata()
    suspend fun updateLastBackupDate(date: Long)
    fun getBackupMetadata(): Flow<BackupMetadataEntity?>
}
