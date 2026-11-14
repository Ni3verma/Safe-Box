package com.andryoga.safebox.worker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.R
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
import com.andryoga.safebox.data.db.docs.export.ExportBackupPayload
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.data.db.secureDao.AuthenticatorDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.domain.models.NotificationOptions
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.security.envelope.interfaces.SessionLockedException
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.BackupFailureReason
import com.andryoga.safebox.worker.backup.BackupCodec
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date
import java.util.UUID

@HiltWorker
class BackupDataWorker
@AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupPasswordRegistry: BackupPasswordRegistry,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val loginDataDaoSecure: LoginDataDaoSecure,
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure,
    private val authenticatorDataDaoSecure: AuthenticatorDataDaoSecure,
    private val analyticsHelper: AnalyticsHelper,
    private val dispatchersProvider: DispatchersProvider,
) : CoroutineWorker(context, params) {
    private val localTag = "backup data worker -> "

    private var startTime = System.currentTimeMillis()

    override suspend fun doWork(): Result {
        // Taken first so no early return leaves it registered; zeroed in finally.
        val password = backupPasswordRegistry.take(id)
        try {
            return if (password == null) {
                onBackupFailure(BackupFailureReason.SESSION_LOCKED, null)
            } else {
                backupWithPassword(password)
            }
        } finally {
            password?.fill(NUL)
        }
    }

    private suspend fun backupWithPassword(password: CharArray): Result {
        return try {
            val backupMetadata = backupMetadataRepository.getBackupMetadata().first()
            if (backupMetadata == null) {
                Timber.i("backup metadata not found")
                Result.success()
            } else {
                backup(backupMetadata, password)
            }
        } catch (exception: CancellationException) {
            // The work was stopped (constraints, cancel, system limit), not failed. WorkManager
            // catches this and reschedules the work; reporting it as UNKNOWN would notify the user.
            throw exception
        } catch (exception: SessionLockedException) {
            onBackupFailure(BackupFailureReason.SESSION_LOCKED, exception)
        } catch (exception: Exception) {
            onBackupFailure(BackupFailureReason.UNKNOWN, exception)
        }
    }

    /**
     * Runs one backup into the folder described by [backupMetadata].
     *
     * Order matters: the empty-vault check runs before the start notification and before the
     * folder is touched, so an empty vault can never clear the folder setting. The folder
     * pre-check runs before encryption, so a dead folder costs no crypto work.
     *
     * @param password the backup file password, registered by whoever enqueued this work.
     * @return [Result.success] only when a backup file was written, otherwise
     * [Result.failure] carrying a [BackupFailureReason].
     */
    private suspend fun backup(backupMetadata: BackupPathData, password: CharArray): Result {
        startTime = System.currentTimeMillis()
        val backupStartedAt = startTime

        val loginData = loginDataDaoSecure.exportAllData()
        val bankAccountData = bankAccountDataDaoSecure.exportAllData()
        val bankCardData = bankCardDataDaoSecure.exportAllData()
        val secureNoteData = secureNoteDataDaoSecure.exportAllData()
        val authenticatorData = authenticatorDataDaoSecure.exportAllData()

        recordTime("got all data")

        if (
            !hasAnyRecord(
                loginData,
                bankAccountData,
                bankCardData,
                secureNoteData,
                authenticatorData,
            )
        ) {
            Timber.i("$localTag nothing to export")
            analyticsHelper.logEvent(AnalyticsKey.BACKUP_DATA_NOTHING_TO_BACKUP)
            // Special case, bypasses onBackupFailure: no notification, since auto-backup runs on
            // every password login, and no folder deletion, since the folder was never touched.
            return Result.failure(BackupFailureReason.NOTHING_TO_BACKUP.toWorkData())
        }

        if (inputData.getBoolean(BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)) {
            sendNotification(
                getNotificationOptions(
                    applicationContext.getString(R.string.notification_backup_in_progress)
                )
            )
        }

        val pickedDir = resolveWritableDir(backupMetadata.uriString)
            ?: return onBackupFailure(BackupFailureReason.FOLDER_INACCESSIBLE, null)

        val backupBytes = BackupCodec.encode(
            password = password,
            payload = ExportBackupPayload(
                logins = loginData,
                bankAccounts = bankAccountData,
                bankCards = bankCardData,
                secureNotes = secureNoteData,
                authenticators = authenticatorData,
            ),
            createdAt = System.currentTimeMillis(),
            appVersionCode = BuildConfig.VERSION_CODE,
        )
        recordTime("encrypted backup")

        val writtenBytes = try {
            deleteExtraBackupFiles(pickedDir)
            exportToFile(pickedDir, backupBytes)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return onBackupFailure(classifyWriteFailure(exception), exception)
        }

        recordTime("exported to file, updating date in db")
        backupMetadataRepository.updateLastBackupDate(System.currentTimeMillis())
        sendNotification(
            getNotificationOptions(applicationContext.getString(R.string.notification_backup_success))
        )
        analyticsHelper.logEvent(AnalyticsKey.BACKUP_DATA_SUCCESS) {
            param(AnalyticsParam.VERSION, CommonConstants.BACKUP_VERSION.toDouble())
            param(AnalyticsParam.DURATION_MS, System.currentTimeMillis() - backupStartedAt)
            param(
                AnalyticsParam.SIZE_KB,
                if (writtenBytes < 0) UNKNOWN_SIZE else writtenBytes / BYTES_PER_KIB,
            )
        }
        return Result.success()
    }

    /**
     * Resolves the saved backup folder and checks that a file can be written into it.
     *
     * @return the folder, or null when it no longer exists, is not a directory, or its write
     * access (including a revoked persisted SAF grant) is gone.
     */
    private fun resolveWritableDir(uriString: String): DocumentFile? {
        val dir = runCatching {
            val uri = uriString.toUri()
            val path = uri.path
            if (uri.scheme == "file" && path != null) {
                DocumentFile.fromFile(File(path))
            } else {
                DocumentFile.fromTreeUri(applicationContext, uri)
            }
        }.getOrNull()
        return dir?.takeIf { it.exists() && it.isDirectory && it.canWrite() }
    }

    private fun sendNotification(notificationOptions: NotificationOptions) {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Utils.makeStatusNotification(
                applicationContext,
                notificationOptions
            )
        }
    }

    /**
     * Reports a failed backup: logs analytics, clears the folder setting only for
     * [BackupFailureReason.FOLDER_INACCESSIBLE], and posts the reason's notification.
     *
     * @return [Result.failure] carrying [reason] for the manual backup dialog.
     */
    private suspend fun onBackupFailure(
        reason: BackupFailureReason,
        exception: Exception?,
    ): Result {
        if (reason == BackupFailureReason.SESSION_LOCKED) {
            // An expected state, not a defect: no non-fatal for it.
            Timber.i("$localTag backup abandoned, the vault is locked")
        } else {
            Timber.e(exception, "$localTag backup failed, reason = $reason")
        }
        analyticsHelper.logEvent(AnalyticsKey.BACKUP_DATA_FAILURE) {
            param(AnalyticsParam.REASON, reason.name)
            param(AnalyticsParam.MESSAGE, exception?.message.orEmpty())
        }
        if (reason == BackupFailureReason.FOLDER_INACCESSIBLE) {
            backupMetadataRepository.deleteBackupMetadata()
        }
        val notificationRes = when (reason) {
            BackupFailureReason.FOLDER_INACCESSIBLE -> R.string.notification_backup_failure
            BackupFailureReason.WRITE_FAILED -> R.string.backup_write_failed_message
            BackupFailureReason.UNKNOWN -> R.string.backup_unknown_error_message
            // Nothing to tell the user: an empty vault, or a lock already taking them to Login.
            BackupFailureReason.NOTHING_TO_BACKUP,
            BackupFailureReason.SESSION_LOCKED,
            -> null
        }
        notificationRes?.let {
            sendNotification(getNotificationOptions(applicationContext.getString(it)))
        }
        return Result.failure(reason.toWorkData())
    }

    private suspend fun deleteExtraBackupFiles(pickedDir: DocumentFile) =
        withContext(dispatchersProvider.io) {
            val files = pickedDir.listFiles().filter {
                it.isFile && it.name != null &&
                        it.name!!.startsWith("SafeBoxBackup") &&
                        it.name!!.contains(".bak")
            }
            if (files.size >= CommonConstants.MAX_BACKUP_FILES) {
                Timber.i("max backup files threshold reached")
                val sortedFiles = files.sortedBy {
                    it.name
                }
                for (i in 0..(files.size - CommonConstants.MAX_BACKUP_FILES)) {
                    Timber.i("deleting backup file ${sortedFiles[i].name}")
                    sortedFiles[i].delete()
                }
            }
        }

    /**
     * Writes [backupBytes] to a new file in [pickedDir].
     *
     * @return the written file's size in bytes, or -1 when the provider cannot report it. Read
     * for analytics only; the write itself is unchanged.
     */
    private suspend fun exportToFile(
        pickedDir: DocumentFile,
        backupBytes: ByteArray,
    ): Long = withContext(dispatchersProvider.io) {
        val nameSuffix =
            Utils.getFormattedDate(Date(), "yyyyMMddHHmmssSSS") + ".bak"
        val fileName = "SafeBoxBackup$nameSuffix"
        val uri = pickedDir.uri
        if (BackupStorageUtils.isRawFileScheme(uri)) {
            val path = uri.path ?: throw IllegalArgumentException("Backup directory path is null")
            val targetFile = File(path, fileName)
            Timber.i("$localTag making output stream for file path: ${targetFile.absolutePath}")
            FileOutputStream(targetFile).use {
                Timber.i("$localTag writing to backup file")
                it.write(backupBytes)
            }
            targetFile.length()
        } else {
            Timber.i("$localTag  creating file")
            val file = pickedDir.createFile("application/octet-stream", fileName)
                ?: throw IOException("Failed to create backup file in the backup folder")
            Timber.i("$localTag opening file descriptor / output stream")
            val fileUri = file.uri
            applicationContext.contentResolver.openFileDescriptor(
                fileUri,
                "w"
            )?.use { parcelFileDescriptor ->
                Timber.i("$localTag making output stream")
                FileOutputStream(parcelFileDescriptor.fileDescriptor).use {
                    Timber.i("$localTag writing to backup file")
                    it.write(backupBytes)
                }
            } ?: throw IOException("Failed to open file descriptor for URI: $fileUri")
            // telemetry only: a provider that cannot answer must not fail a backup that was written.
            // DocumentFile.length() is 0 when the provider cannot report it; a written backup is never empty.
            val reportedLength = runCatching { file.length() }.getOrDefault(UNKNOWN_SIZE)
            if (reportedLength > 0) reportedLength else UNKNOWN_SIZE
        }
    }

    private fun recordTime(message: String) {
        val timeTook = System.currentTimeMillis() - startTime
        val sec = CommonConstants.TIME_1_SECOND
        Timber.i("$localTag  $message : time took = $timeTook millis, ${timeTook / sec} sec")
        startTime = System.currentTimeMillis()
    }

    private fun hasAnyRecord(vararg list: List<Any>): Boolean {
        list.forEach {
            if (it.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun getNotificationOptions(notificationContent: String): NotificationOptions {
        return NotificationOptions(
            applicationContext.getString(R.string.notification_backup_channel_id),
            0,
            applicationContext.getString(R.string.notification_backup_channel_name),
            applicationContext.getString(R.string.notification_backup_channel_desc),
            NotificationManager.IMPORTANCE_DEFAULT,
            R.drawable.ic_backup_restore,
            applicationContext.getString(R.string.notification_backup_title),
            notificationContent,
            NotificationCompat.PRIORITY_HIGH
        )
    }

    companion object {
        private const val BYTES_PER_KIB = 1024L

        /** `size_kb` when the document provider cannot report the written file's length. */
        private const val UNKNOWN_SIZE = -1L

        private const val NUL = '\u0000'

        /**
         * Registers [password] under the request id, then enqueues: the worker may start at once.
         *
         * @return the work id, for observing the request.
         */
        fun enqueueRequest(
            password: String,
            showBackupStartNotification: Boolean,
            workManager: WorkManager,
            backupPasswordRegistry: BackupPasswordRegistry,
        ): UUID {
            val backupDataRequest = OneTimeWorkRequestBuilder<BackupDataWorker>()
                .setInputData(
                    Data.Builder()
                        .putBoolean(
                            BACKUP_PARAM_IS_SHOW_START_NOTIFICATION,
                            showBackupStartNotification,
                        )
                        .build(),
                )
                .build()

            backupPasswordRegistry.put(backupDataRequest.id, password)
            workManager.enqueueUniqueWork(
                CommonConstants.WORKER_NAME_BACKUP_DATA,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                backupDataRequest
            )

            Timber.i("backup work req enqueued")
            return backupDataRequest.id
        }
    }
}

/**
 * Maps an exception thrown while writing the backup file to a [BackupFailureReason].
 *
 * [SecurityException] and [FileNotFoundException] mean the folder or its grant disappeared after
 * the pre-check, so the folder setting must be cleared. Any other [IOException] (e.g. disk full)
 * keeps the folder, because retrying into the same folder can succeed once space is freed.
 *
 * @param throwable the exception thrown by the write step.
 * @return the reason reported to analytics, the notification and the manual backup dialog.
 */
internal fun classifyWriteFailure(throwable: Throwable): BackupFailureReason = when (throwable) {
    is SecurityException, is FileNotFoundException -> BackupFailureReason.FOLDER_INACCESSIBLE
    is IOException -> BackupFailureReason.WRITE_FAILED
    else -> BackupFailureReason.UNKNOWN
}
