package com.andryoga.safebox.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.secureDao.AuthenticatorDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.security.envelope.interfaces.SessionLockedException
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.RestoreFailureReason
import com.andryoga.safebox.worker.backup.BackupCodecV4
import com.andryoga.safebox.worker.backup.CorruptBackupException
import com.andryoga.safebox.worker.backup.LegacyBackupReader
import com.andryoga.safebox.worker.backup.OpenedBackup
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.security.GeneralSecurityException
import java.util.Date
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException

@HiltWorker
class RestoreDataWorker
@AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupPasswordRegistry: BackupPasswordRegistry,
    private val legacyBackupReader: LegacyBackupReader,
    private val safeBoxDatabase: SafeBoxDatabase,
    private val loginDataDaoSecure: LoginDataDaoSecure,
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure,
    private val authenticatorDataDaoSecure: AuthenticatorDataDaoSecure,
    private val totpGenerator: TotpGenerator,
    private val analyticsHelper: AnalyticsHelper,
) : CoroutineWorker(context, params) {

    private val localTag = "restore data worker -> "

    private var startTime = System.currentTimeMillis()

    /** Set at doWork start; [startTime] is reset by every [recordTime] so it cannot serve. */
    private var restoreStartedAt = startTime

    private lateinit var inputPassword: CharArray

    override suspend fun doWork(): Result {
        startTime = System.currentTimeMillis()
        restoreStartedAt = startTime
        // Taken first so no early return leaves it registered; zeroed in finally.
        val password = backupPasswordRegistry.take(id)
        try {
            if (password == null) {
                Timber.i("$localTag restore abandoned, no password for this work in this process")
                return Result.failure(RestoreFailureReason.SESSION_LOCKED.toWorkData())
            }
            inputPassword = password
            return restore()
        } finally {
            password?.fill(NUL)
        }
    }

    private suspend fun restore(): Result {
        // 0 until the container is read, so a failure before that reports an unknown version.
        var version = 0
        return try {
            val fileUri = inputData.getString(CommonConstants.RESTORE_PARAM_FILE_URI)
                ?: throw IllegalArgumentException("expected file uri input was not received")

            recordTime("got file uri")

            val bytes = openRestoreFile(fileUri).use(::readWithinLimit)
            val backup = openBackup(bytes)
            version = backup.version
            // A newer build may have changed the payload in ways this build cannot read, which
            // would otherwise surface as a misleading "corrupt file". Older versions are still
            // accepted. Checked before any password work.
            if (version > CommonConstants.BACKUP_VERSION) {
                analyticsHelper.logEvent(AnalyticsKey.RESTORE_DATA_BACKUP_TOO_NEW) {
                    param(AnalyticsParam.VERSION, version.toDouble())
                }
                return Result.failure(RestoreFailureReason.BACKUP_TOO_NEW.toWorkData())
            }
            val decoded = backup.decode(inputPassword)
            Timber.i(
                "$localTag version = $version, " +
                        "created on : ${Utils.getFormattedDate(Date(decoded.createdAt))}"
            )
            recordTime("file read and decrypted")
            // Only an encrypted payload can prove a format-1–3 password: a file without one
            // decrypted nothing, and the destructive replace would wipe the vault for any password
            // (#272). No genuine backup looks like that, because BackupDataWorker writes no file
            // for an empty vault. A payload that decrypts to an empty list still counts.
            if (decoded.hasNoRecordPayload) {
                analyticsHelper.logEvent(AnalyticsKey.RESTORE_DATA_BACKUP_EMPTY) {
                    param(AnalyticsParam.VERSION, version.toDouble())
                }
                return Result.failure(RestoreFailureReason.BACKUP_EMPTY.toWorkData())
            }
            val authenticatorData = filterDecodableAuthenticatorData(
                decoded.authenticators,
                decoded.undecodableAuthenticatorCount,
            )
            restoreDataToDb(
                decoded.logins,
                decoded.bankAccounts,
                decoded.bankCards,
                decoded.secureNotes,
                authenticatorData,
            )
            analyticsHelper.logEvent(AnalyticsKey.RESTORE_DATA_SUCCESS) {
                param(AnalyticsParam.VERSION, version.toDouble())
                param(AnalyticsParam.DURATION_MS, System.currentTimeMillis() - restoreStartedAt)
            }
            Result.success()
        } catch (badPaddingException: BadPaddingException) {
            // A format-4 key check failure is an AEADBadTagException, a BadPaddingException too.
            Timber.e(badPaddingException, "wrong password entered for restore")
            analyticsHelper.logEvent(AnalyticsKey.RESTORE_DATA_WRONG_PASSWORD) {
                param(AnalyticsParam.VERSION, version.toDouble())
            }
            Result.failure(RestoreFailureReason.INCORRECT_PASSWORD.toWorkData())
        } catch (exception: SessionLockedException) {
            // An expected state, not a defect: the transaction, if reached, has rolled back.
            Timber.i("$localTag restore abandoned, the vault is locked")
            Result.failure(RestoreFailureReason.SESSION_LOCKED.toWorkData())
        } catch (exception: Exception) {
            val failureReason = mapExceptionToFailureReason(exception)
            onRestoreError(exception, failureReason, version)
            Result.failure(failureReason.toWorkData())
        }
    }

    private fun openRestoreFile(fileUri: String): InputStream {
        val uri = fileUri.toUri()
        return if (BackupStorageUtils.isRawFileScheme(uri)) {
            val path = uri.path ?: throw IllegalArgumentException("Restore file path is null")
            FileInputStream(File(path))
        } else {
            applicationContext.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open input stream for $uri")
        }
    }

    /**
     * Reads the whole file, refusing one longer than any well-formed backup before it is
     * buffered in full.
     *
     * @throws CorruptBackupException When the file exceeds [BackupCodecV4.MAX_FILE_BYTES].
     */
    private fun readWithinLimit(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return output.toByteArray()
            output.write(buffer, 0, read)
            if (output.size() > BackupCodecV4.MAX_FILE_BYTES) {
                throw CorruptBackupException("file larger than any backup")
            }
        }
    }

    /** Routes on the first bytes: format 4 has its magic, formats 1–3 are a Java serialisation stream. */
    private fun openBackup(bytes: ByteArray): OpenedBackup = when {
        BackupCodecV4.hasMagic(bytes) -> BackupCodecV4.open(bytes)
        LegacyBackupReader.hasStreamHeader(bytes) -> legacyBackupReader.open(bytes)
        else -> throw CorruptBackupException("not a Safe-Box backup")
    }

    private fun mapExceptionToFailureReason(exception: Exception): RestoreFailureReason {
        return when (exception) {
            is IOException,
            is IllegalArgumentException,
            is IllegalStateException,
            is NullPointerException,
            is ClassCastException,
            is BufferUnderflowException,
            is IllegalBlockSizeException,
            is GeneralSecurityException -> RestoreFailureReason.CORRUPT_OR_INVALID_FILE

            else -> RestoreFailureReason.UNKNOWN_ERROR
        }
    }

    private fun onRestoreError(exception: Exception, reason: RestoreFailureReason, version: Int) {
        Timber.e(
            exception,
            "$localTag exception occurred : ${exception.localizedMessage}"
        )
        analyticsHelper.logEvent(AnalyticsKey.RESTORE_DATA_FAILURE) {
            param(AnalyticsParam.VERSION, version.toDouble())
            param(AnalyticsParam.REASON, reason.name)
        }
    }

    /**
     * Drops authenticator records that cannot produce a code.
     *
     * Successful decryption and deserialization prove the backup is authentic and well formed,
     * they prove nothing about whether the record is semantically usable. An undecodable seed, an
     * unsupported digit count or a non-positive period all make [TotpGenerator.generateCode] throw,
     * so such records are skipped instead of being persisted.
     *
     * Only the offending records are dropped rather than failing the whole restore, because one
     * bad 2FA seed must not cost the user every login, card and note in the backup.
     *
     * @param authenticatorData Records decoded from the backup, or null when the backup has none.
     * @param deserializationFailedCount Elements the reader could not deserialize at all. They
     * leave no record to inspect here, so they are counted rather than filtered.
     * @return Records safe to persist, or null when the input was null.
     */
    private fun filterDecodableAuthenticatorData(
        authenticatorData: List<ExportAuthenticatorData>?,
        deserializationFailedCount: Int,
    ): List<ExportAuthenticatorData>? {
        if (authenticatorData == null) return null

        val (decodable, undecodable) = authenticatorData.partition {
            totpGenerator.isValidConfig(it.toTotpConfig())
        }

        val skippedCount = deserializationFailedCount + undecodable.size
        if (skippedCount > 0) {
            // never log the seed itself, only how many were dropped.
            Timber.w("$localTag skipped $skippedCount authenticator records, invalid secret or schema")
            analyticsHelper.logEvent(AnalyticsKey.RESTORE_INVALID_AUTHENTICATOR_SKIPPED) {
                param(AnalyticsParam.COUNT, skippedCount)
            }
        }

        return decodable
    }

    /**
     * Reads the generation parameters out of a backup record.
     *
     * @return Config for this record, not yet validated.
     */
    private fun ExportAuthenticatorData.toTotpConfig(): TotpConfig {
        return TotpConfig(
            secretKey = secretKey,
            algorithm = algorithm,
            digits = digits,
            period = period,
        )
    }

    private fun restoreDataToDb(
        loginData: List<ExportLoginData>?,
        bankAccountData: List<ExportBankAccountData>?,
        bankCardData: List<ExportBankCardData>?,
        secureNoteData: List<ExportSecureNoteData>?,
        authenticatorData: List<ExportAuthenticatorData>?,
    ) {
        Timber.i("starting transaction")
        safeBoxDatabase.runInTransaction {
            loginDataDaoSecure.deleteAllData()
            loginData?.let {
                loginDataDaoSecure.insertMultipleLoginData(
                    loginData.map {
                        LoginDataEntity(
                            0,
                            it.title,
                            it.url,
                            it.password,
                            it.notes,
                            it.userId,
                            Date(it.creationDate),
                            Date(it.updateDate)
                        )
                    }
                )
            }
            recordTime("restored login data")

            bankAccountDataDaoSecure.deleteAllData()
            bankAccountData?.let {
                bankAccountDataDaoSecure.insertMultipleBankAccountData(
                    bankAccountData.map {
                        BankAccountDataEntity(
                            0, it.title, it.accountNumber, it.customerName, it.customerId,
                            it.branchCode, it.branchName, it.branchAddress, it.ifscCode,
                            it.micrCode, it.notes, Date(it.creationDate), Date(it.updateDate)
                        )
                    }
                )
            }
            recordTime("restored bank account data")

            bankCardDataDaoSecure.deleteAllData()
            bankCardData?.let {
                bankCardDataDaoSecure.insertMultipleBankCardData(
                    bankCardData.map {
                        BankCardDataEntity(
                            0, it.title, it.name, it.number, it.pin, it.cvv,
                            it.expiryDate, it.notes,
                            Date(it.creationDate), Date(it.updateDate)
                        )
                    }
                )
            }
            recordTime("restored bank card data")

            secureNoteDataDaoSecure.deleteAllData()
            secureNoteData?.let {
                secureNoteDataDaoSecure.insertMultipleSecureNoteData(
                    secureNoteData.map {
                        SecureNoteDataEntity(
                            0,
                            it.title,
                            it.notes,
                            Date(it.creationDate),
                            Date(it.updateDate)
                        )
                    }
                )
            }
            recordTime("restored secure note data")

            authenticatorDataDaoSecure.deleteAllData()
            authenticatorData?.let {
                authenticatorDataDaoSecure.insertMultipleAuthenticatorData(
                    authenticatorData.map {
                        val config = it.toTotpConfig()
                        AuthenticatorDataEntity(
                            key = 0,
                            title = it.title,
                            secretKey = config.secretKey,
                            algorithm = config.algorithm,
                            digits = config.digits,
                            period = config.period,
                            creationDate = Date(it.creationDate),
                            updateDate = Date(it.updateDate),
                        )
                    },
                )
            }
            recordTime("restored authenticator data")
        }
    }

    private fun recordTime(message: String) {
        val timeTook = System.currentTimeMillis() - startTime
        val sec = CommonConstants.TIME_1_SECOND
        Timber.i("$localTag  $message : time took = $timeTook millis, ${timeTook / sec} sec")
        startTime = System.currentTimeMillis()
    }

    private companion object {
        const val NUL = '\u0000'
    }
}
