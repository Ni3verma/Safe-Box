package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.entity.BackupMetadataEntity

interface BackupMetadataRepository {
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)
    suspend fun deleteBackupMetadata(backupMetadataEntity: BackupMetadataEntity)
    suspend fun updateLastBackupDate(date: Long)
    suspend fun isBackupPathSet(): Boolean
}
