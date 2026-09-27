package com.andryoga.safebox.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.Utils.getFormattedDate
import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.domain.models.backup.BackupPathData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

class BackupMetadataRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupMetadataDao: BackupMetadataDao,
    private val analyticsHelper: AnalyticsHelper
) : BackupMetadataRepository {
    private val contentResolver = context.contentResolver

    override suspend fun insertBackupMetadata(uriPath: Uri?): Boolean {
        var permissionGranted = uriPath != null
        if (uriPath != null) {
            if (uriPath.scheme == "content" || uriPath.toString().startsWith("content://")) {
                runCatching {
                    val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uriPath, flags)
                }.onFailure {
                    permissionGranted = false
                    Timber.w(
                        it,
                        "Failed to take persistable URI permission for authority=%s",
                        uriPath.authority ?: "unknown"
                    )
                }
            }
            if (permissionGranted) {
                val previousUriString = storedUriString()
                backupMetadataDao.insertBackupMetadata(
                    BackupMetadataEntity(
                        key = 1,
                        uriString = uriPath.toString(),
                        displayPath = uriPath.path ?: uriPath.toString(),
                        lastBackupDate = null,
                        createdOn = Date()
                    )
                )
                // re-picking the same folder reuses its grant, so only a replaced folder is released
                if (previousUriString != null && previousUriString != uriPath.toString()) {
                    releasePersistedPermission(previousUriString)
                }
            }
        }

        analyticsHelper.logEvent(AnalyticsKey.BACKUP_SELECT_DIR_RESULT) {
            param(AnalyticsParam.RESULT, permissionGranted)
        }
        return permissionGranted
    }

    override suspend fun deleteBackupMetadata() {
        storedUriString()?.let { releasePersistedPermission(it) }
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

    private suspend fun storedUriString(): String? =
        backupMetadataDao.getBackupMetadata().first()?.uriString

    /**
     * Releases the persisted SAF grant of a folder the app no longer uses. Non-content URIs hold
     * no grant. A failure is ignored: the grant may already be gone, e.g. the folder was deleted.
     *
     * @param uriString the stored folder URI whose grant should be released.
     */
    private fun releasePersistedPermission(uriString: String) {
        if (!uriString.startsWith("content://")) return
        runCatching {
            val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.releasePersistableUriPermission(uriString.toUri(), flags)
        }
    }
}
