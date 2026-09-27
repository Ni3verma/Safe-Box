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
import com.andryoga.safebox.R
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_PASSWORD
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
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
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.BackupFailureReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream
import java.nio.ByteBuffer
import java.util.Date
import java.util.UUID

@HiltWorker
class BackupDataWorker
@AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val passwordBasedEncryption: PasswordBasedEncryption,
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

    private lateinit var salt: ByteArray
    private lateinit var iv: ByteArray

    private val exportMap = mutableMapOf<String, ByteArray?>()

    override suspend fun doWork(): Result {
        return try {
            val backupMetadata = backupMetadataRepository.getBackupMetadata().first()
            if (backupMetadata == null) {
                Timber.i("backup metadata not found")
                Result.success()
            } else {
                backup(backupMetadata)
            }
        } catch (exception: CancellationException) {
            throw exception
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
     * @return [Result.success] only when a backup file was written, otherwise
     * [Result.failure] carrying a [BackupFailureReason].
     */
    private suspend fun backup(backupMetadata: BackupPathData): Result {
        startTime = System.currentTimeMillis()

        val inputPassword = inputData.getString(BACKUP_PARAM_PASSWORD)
            ?: throw IllegalArgumentException("expected password input was not received")

        val loginData = loginDataDaoSecure.exportAllData()
        val bankAccountData = bankAccountDataDaoSecure.exportAllData()
        val bankCardData = bankCardDataDaoSecure.exportAllData()
        val secureNoteData = secureNoteDataDaoSecure.exportAllData()
        val authenticatorData = authenticatorDataDaoSecure.exportAllData()

        recordTime("got all data")

        if (
            !shouldExport(
                loginData,
                bankAccountData,
                bankCardData,
                secureNoteData,
                authenticatorData,
            )
        ) {
            Timber.i("$localTag nothing to export")
            analyticsHelper.logEvent(AnalyticsKey.BACKUP_DATA_NOTHING_TO_BACKUP)
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

        salt = passwordBasedEncryption.getRandomSalt()
        iv = passwordBasedEncryption.getRandomIV()
        exportMap.putAll(
            mapOf(
                CommonConstants.SALT_KEY to salt,
                CommonConstants.IV_KEY to iv,
                CommonConstants.VERSION_KEY to ByteArray(1) {
                    CommonConstants.BACKUP_VERSION.toByte()
                },
            )
        )
        recordTime("got salt and iv")

        populateExportMapWithData(
            loginData,
            inputPassword,
            bankAccountData,
            bankCardData,
            secureNoteData,
            authenticatorData,
        )

        try {
            deleteExtraBackupFiles(pickedDir)
            exportToFile(pickedDir)
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
        analyticsHelper.logEvent(AnalyticsKey.BACKUP_DATA_SUCCESS)
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
        Timber.e(exception, "$localTag backup failed, reason = $reason")
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
            BackupFailureReason.NOTHING_TO_BACKUP -> null
        }
        notificationRes?.let {
            sendNotification(getNotificationOptions(applicationContext.getString(it)))
        }
        return Result.failure(reason.toWorkData())
    }

    private fun populateExportMapWithData(
        loginData: List<ExportLoginData>,
        inputPassword: String,
        bankAccountData: List<ExportBankAccountData>,
        bankCardData: List<ExportBankCardData>,
        secureNoteData: List<ExportSecureNoteData>,
        authenticatorData: List<ExportAuthenticatorData>,
    ) {
        exportMap[CommonConstants.LOGIN_DATA_KEY] = encryptLoginData(loginData, inputPassword)
        recordTime("got login data byte array")

        exportMap[CommonConstants.BANK_ACCOUNT_DATA_KEY] =
            encryptBankAccountData(bankAccountData, inputPassword)
        recordTime("got bank account data byte array")

        exportMap[CommonConstants.BANK_CARD_DATA_KEY] =
            encryptBankCardData(bankCardData, inputPassword)
        recordTime("got bank card data byte array")

        exportMap[CommonConstants.SECURE_NOTE_DATA_KEY] =
            encryptSecureNoteData(secureNoteData, inputPassword)
        recordTime("got secure note data byte array")

        exportMap[CommonConstants.AUTHENTICATOR_DATA_KEY] =
            encryptAuthenticatorData(authenticatorData, inputPassword)
        recordTime("got authenticator data byte array")

        exportMap[CommonConstants.CREATION_DATE_KEY] =
            ByteBuffer.allocate(Long.SIZE_BYTES)
                .putLong(System.currentTimeMillis())
                .array()
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

    private suspend fun exportToFile(
        pickedDir: DocumentFile
    ) = withContext(dispatchersProvider.io) {
        val nameSuffix =
            Utils.getFormattedDate(Date(), "yyyyMMddHHmmssSSS") + ".bak"
        val fileName = "SafeBoxBackup$nameSuffix"
        val uri = pickedDir.uri
        if (BackupStorageUtils.isRawFileScheme(uri)) {
            val path = uri.path ?: throw IllegalArgumentException("Backup directory path is null")
            val targetFile = File(path, fileName)
            Timber.i("$localTag making output stream for file path: ${targetFile.absolutePath}")
            ObjectOutputStream(FileOutputStream(targetFile)).use {
                Timber.i("$localTag writing to backup file")
                it.writeObject(exportMap)
            }
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
                ObjectOutputStream(FileOutputStream(parcelFileDescriptor.fileDescriptor)).use {
                    Timber.i("$localTag writing to backup file")
                    it.writeObject(exportMap)
                }
            } ?: throw IOException("Failed to open file descriptor for URI: $fileUri")
        }
    }

    private fun encryptLoginData(
        data: List<ExportLoginData>,
        inputPassword: String
    ): ByteArray? {
        if (data.isNotEmpty()) {
            val json = Json.encodeToString(ListSerializer(ExportLoginData.serializer()), data)
            return passwordBasedEncryption.encryptDecrypt(
                symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                json.toByteArray(),
                salt,
                iv,
                true
            )
        }
        return null
    }

    private fun encryptBankAccountData(
        data: List<ExportBankAccountData>,
        inputPassword: String
    ): ByteArray? {
        if (data.isNotEmpty()) {
            val json = Json.encodeToString(ListSerializer(ExportBankAccountData.serializer()), data)
            return passwordBasedEncryption.encryptDecrypt(
                symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                json.toByteArray(),
                salt,
                iv,
                true
            )
        }
        return null
    }

    private fun encryptBankCardData(
        data: List<ExportBankCardData>,
        inputPassword: String
    ): ByteArray? {
        if (data.isNotEmpty()) {
            val json = Json.encodeToString(ListSerializer(ExportBankCardData.serializer()), data)
            return passwordBasedEncryption.encryptDecrypt(
                symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                json.toByteArray(),
                salt,
                iv,
                true
            )
        }
        return null
    }

    private fun encryptSecureNoteData(
        data: List<ExportSecureNoteData>,
        inputPassword: String
    ): ByteArray? {
        if (data.isNotEmpty()) {
            val json = Json.encodeToString(ListSerializer(ExportSecureNoteData.serializer()), data)
            return passwordBasedEncryption.encryptDecrypt(
                symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                json.toByteArray(),
                salt,
                iv,
                true
            )
        }
        return null
    }

    /**
     * Serializes and encrypts the list of TOTP authenticators using password-based encryption.
     *
     * @param data The authenticator records to encrypt.
     * @param inputPassword The user-supplied backup password (encrypted with symmetric key).
     * @return Encrypted byte array payload, or null if the record list is empty.
     */
    private fun encryptAuthenticatorData(
        data: List<ExportAuthenticatorData>,
        inputPassword: String,
    ): ByteArray? {
        if (data.isNotEmpty()) {
            val json =
                Json.encodeToString(ListSerializer(ExportAuthenticatorData.serializer()), data)
            return passwordBasedEncryption.encryptDecrypt(
                symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                json.toByteArray(),
                salt,
                iv,
                true,
            )
        }
        return null
    }

    private fun recordTime(message: String) {
        val timeTook = System.currentTimeMillis() - startTime
        val sec = CommonConstants.TIME_1_SECOND
        Timber.i("$localTag  $message : time took = $timeTook millis, ${timeTook / sec} sec")
        startTime = System.currentTimeMillis()
    }

    private fun shouldExport(vararg list: List<Any>): Boolean {
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
        fun enqueueRequest(
            password: String,
            showBackupStartNotification: Boolean,
            workManager: WorkManager,
            symmetricKeyUtils: SymmetricKeyUtils
        ): UUID {
            val backupDataRequest = OneTimeWorkRequestBuilder<BackupDataWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(
                            BACKUP_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(password)
                        )
                        .putBoolean(
                            BACKUP_PARAM_IS_SHOW_START_NOTIFICATION,
                            showBackupStartNotification
                        )
                        .build()
                )
                .build()

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
