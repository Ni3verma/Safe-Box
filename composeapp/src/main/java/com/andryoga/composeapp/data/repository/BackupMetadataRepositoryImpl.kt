package com.andryoga.composeapp.data.repository

import com.andryoga.composeapp.common.Utils.getFormattedDate
import com.andryoga.composeapp.data.db.dao.BackupMetadataDao
import com.andryoga.composeapp.data.db.entity.BackupMetadataEntity
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.domain.models.backup.BackupPathData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BackupMetadataRepositoryImpl @Inject constructor(
    private val backupMetadataDao: BackupMetadataDao
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

    override fun getBackupMetadata(): Flow<BackupPathData?> {
        return backupMetadataDao.getBackupMetadata().map { entity ->
            entity?.let {
                BackupPathData(
                    uriString = it.uriString,
                    path = it.displayPath,
                    lastBackupTime = if (it.lastBackupDate == null) "NA" else getFormattedDate(date = it.lastBackupDate)
                )
            }
        }
    }
}
