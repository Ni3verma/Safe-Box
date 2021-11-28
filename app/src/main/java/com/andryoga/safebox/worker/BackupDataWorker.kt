package com.andryoga.safebox.worker

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.CommonConstants.time1Sec
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.NotificationOptions
import com.andryoga.safebox.ui.common.Utils.makeStatusNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.util.*

@HiltWorker
@ExperimentalCoroutinesApi
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
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : CoroutineWorker(context, params) {
    private val localTag = "backup data worker -> "

    private var startTime = System.currentTimeMillis()

    private lateinit var salt: ByteArray
    private lateinit var iv: ByteArray

    private val exportMap = mutableMapOf<String, ByteArray?>()

    override suspend fun doWork(): Result {
        backupMetadataRepository.getBackupMetadata().take(1).collect { backupMetadataEntity ->
            if (backupMetadataEntity != null) {
                Timber.i("backup metadata found")
                val isShowStartNotification =
                    inputData.getBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                if (isShowStartNotification) {
                    makeStatusNotification(
                        applicationContext,
                        getNotificationOptions(
                            applicationContext.getString(R.string.notification_backup_in_progress)
                        )
                    )
                }

                startTime = System.currentTimeMillis()

                val inputPassword = inputData.getString(CommonConstants.BACKUP_PARAM_PASSWORD)
                    ?: throw IllegalArgumentException("expected password input was not received")

                val loginData = loginDataDaoSecure.exportAllData()
                val bankAccountData = bankAccountDataDaoSecure.exportAllData()
                val bankCardData = bankCardDataDaoSecure.exportAllData()
                val secureNoteData = secureNoteDataDaoSecure.exportAllData()

                recordTime("got all data")

                if (
                    shouldExport(loginData, bankAccountData, bankCardData, secureNoteData)
                ) {
                    Timber.i("$localTag data is present for export")
                    salt = passwordBasedEncryption.getRandomSalt()
                    iv = passwordBasedEncryption.getRandomIV()
                    exportMap.putAll(
                        mapOf(
                            CommonConstants.SALT_KEY to salt,
                            CommonConstants.IV_KEY to iv,
                            CommonConstants.VERSION_KEY to ByteArray(1) {
                                CommonConstants.BACKUP_VERSION.toByte()
                            }
                        )
                    )
                    recordTime("got salt and iv")

                    populateExportMapWithData(
                        loginData,
                        inputPassword,
                        bankAccountData,
                        bankCardData,
                        secureNoteData
                    )

                    Timber.i("getting picked dir")
                    try {
                        val pickedDir = DocumentFile.fromTreeUri(
                            applicationContext,
                            Uri.parse(backupMetadataEntity.uriString)
                        )!!

                        deleteExtraBackupFiles(pickedDir)
                        exportToFile(pickedDir)
                    } catch (exception: Exception) {
                        onBackupError(exception)
                    }
                } else {
                    Timber.i("$localTag  nothing to export")
                }
            } else {
                Timber.i("backup metadata not found")
            }
        }

        return Result.Success()
    }

    private suspend fun onBackupError(exception: Exception) {
        Timber.e(
            exception,
            "$localTag exception occurred : ${exception.localizedMessage}"
        )
        Timber.i("removing backup metadata")
        backupMetadataRepository.deleteBackupMetadata()
        makeStatusNotification(
            applicationContext,
            getNotificationOptions(applicationContext.getString(R.string.notification_backup_failure))
        )
    }

    private fun populateExportMapWithData(
        loginData: List<ExportLoginData>,
        inputPassword: String,
        bankAccountData: List<ExportBankAccountData>,
        bankCardData: List<ExportBankCardData>,
        secureNoteData: List<ExportSecureNoteData>
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

        exportMap[CommonConstants.CREATION_DATE_KEY] = ByteArray(1) {
            System.currentTimeMillis().toByte()
        }
    }

    private suspend fun deleteExtraBackupFiles(pickedDir: DocumentFile) =
        withContext(Dispatchers.IO) {
            val files = pickedDir.listFiles().filter {
                it.isFile && it.name != null &&
                    it.name!!.endsWith(".bak") &&
                    it.name!!.startsWith("SafeBoxBackup")
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
    ) = withContext(Dispatchers.IO) {
        val nameSuffix =
            Utils.getFormattedDate(Date(), "yyyyMMddHHmmssSSS") + ".bak"
        Timber.i("$localTag  creating file")
        val file = pickedDir.createFile("application/octet-stream", "SafeBoxBackup$nameSuffix")

        Timber.i("$localTag opening file descriptor")
        applicationContext.contentResolver.openFileDescriptor(
            file!!.uri,
            "w"
        )?.use { parcelFileDescriptor ->
            Timber.i("$localTag making output stream")
            ObjectOutputStream(FileOutputStream(parcelFileDescriptor.fileDescriptor)).use {
                Timber.i("$localTag writing to backup file")
                it.writeObject(exportMap)
            }
        }
        recordTime("exported to file, updating date in db")
        backupMetadataRepository.updateLastBackupDate(System.currentTimeMillis())
        makeStatusNotification(
            applicationContext,
            getNotificationOptions(applicationContext.getString(R.string.notification_backup_success))
        )
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
                salt, iv, true
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
                salt, iv, true
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
                salt, iv, true
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
                salt, iv, true
            )
        }
        return null
    }

    private fun recordTime(message: String) {
        val timeTook = System.currentTimeMillis() - startTime
        val sec = time1Sec
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
            NotificationManager.IMPORTANCE_HIGH,
            R.drawable.ic_backup_restore,
            applicationContext.getString(R.string.notification_backup_title),
            notificationContent,
            NotificationCompat.PRIORITY_HIGH
        )
    }
}
