package com.andryoga.composeapp.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.andryoga.composeapp.common.AnalyticsKeys.BACKUP_SELECT_DIR_RESULT
import com.andryoga.composeapp.common.AnalyticsKeys.RESULT
import com.andryoga.composeapp.common.Utils.getFormattedDate
import com.andryoga.composeapp.data.db.dao.BackupMetadataDao
import com.andryoga.composeapp.data.db.entity.BackupMetadataEntity
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.domain.models.backup.BackupPathData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject

class BackupMetadataRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupMetadataDao: BackupMetadataDao
) : BackupMetadataRepository {
    private val contentResolver = context.contentResolver

    override suspend fun insertBackupMetadata(uriPath: Uri?) {
        Firebase.analytics.logEvent(BACKUP_SELECT_DIR_RESULT) {
            param(RESULT, (uriPath != null && uriPath.path != null).toString())
        }

        if (uriPath != null) {
            val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uriPath, flags)
            backupMetadataDao.insertBackupMetadata(
                BackupMetadataEntity(
                    key = 1,
                    uriString = uriPath.toString(),
                    displayPath = uriPath.path!!,
                    lastBackupDate = null,
                    createdOn = Date()
                )
            )
        }
    }

    override suspend fun deleteBackupMetadata() {
        backupMetadataDao.deleteBackupMetadata()
    }

    override suspend fun updateLastBackupDate(date: Long) {
        backupMetadataDao.updateLastBackupDate(date)
    }

    override suspend fun isBackupPathSet(): Boolean {
        return backupMetadataDao.isBackupPathSet() != 0
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
