package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import javax.inject.Inject

class BackupMetadataRepositoryImpl @Inject constructor(
    private val backupMetadataDao: BackupMetadataDao
) : BackupMetadataRepository {
    override suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity) {
        backupMetadataDao.insertBackupMetadata(backupMetadataEntity)
    }

    override suspend fun deleteBackupMetadata(backupMetadataEntity: BackupMetadataEntity) {
        backupMetadataDao.deleteBackupMetadata(backupMetadataEntity)
    }

    override suspend fun updateLastBackupDate(date: Long) {
        backupMetadataDao.updateLastBackupDate(date)
    }

    override suspend fun isBackupPathSet(): Boolean {
        return backupMetadataDao.getBackupMetadataCount() > 0
    }
}
