package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import kotlinx.coroutines.flow.Flow

interface BackupMetadataRepository {
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)
    suspend fun deleteBackupMetadata(backupMetadataEntity: BackupMetadataEntity)
    suspend fun updateLastBackupDate(date: Long)
    fun getBackupMetadata(): Flow<BackupMetadataEntity?>
}
