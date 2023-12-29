package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BackupMetadataRepositoryImpl
    @Inject
    constructor(
        private val backupMetadataDao: BackupMetadataDao,
    ) : BackupMetadataRepository {
        override suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity) {
            backupMetadataDao.insertBackupMetadata(backupMetadataEntity)
        }

        override suspend fun deleteBackupMetadata() {
            backupMetadataDao.deleteBackupMetadata()
        }

        override suspend fun updateLastBackupDate(date: Long) {
            backupMetadataDao.updateLastBackupDate(date)
        }

        override fun getBackupMetadata(): Flow<BackupMetadataEntity?> {
            return backupMetadataDao.getBackupMetadata()
        }
    }
