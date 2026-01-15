package com.andryoga.safebox.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.common.AnalyticsKeys
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.util.Date
import javax.crypto.BadPaddingException

class RestoreDataWorker(
    context: Context,
    params: WorkerParameters,
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val passwordBasedEncryption: PasswordBasedEncryption,
    private val safeBoxDatabase: SafeBoxDatabase,
    private val loginDataDaoSecure: LoginDataDaoSecure,
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : CoroutineWorker(context, params) {

    private val localTag = "restore data worker -> "

    private var startTime = System.currentTimeMillis()

    private lateinit var importMap: Map<String, ByteArray?>
    private lateinit var salt: ByteArray
    private lateinit var iv: ByteArray
    private lateinit var inputPassword: String

    override suspend fun doWork(): Result {
        startTime = System.currentTimeMillis()
        inputPassword = inputData.getString(CommonConstants.RESTORE_PARAM_PASSWORD)
            ?: throw IllegalArgumentException("expected password input was not received")
        val fileUri = inputData.getString(CommonConstants.RESTORE_PARAM_FILE_URI)
            ?: throw IllegalArgumentException("expected file uri input was not received")

        recordTime("got input pswrd and file uri")

        ObjectInputStream(
            applicationContext.contentResolver.openInputStream(Uri.parse(fileUri))
        ).use {
            val fileObject = it.readObject()
            if (fileObject !is Map<*, *>) {
                throw InvalidObjectException("input file is not correct, was not able to read it is as Map")
            }

            importMap = fileObject as Map<String, ByteArray?>
            val version = importMap[CommonConstants.VERSION_KEY]!![0].toInt()
            val creationDate = importMap[CommonConstants.CREATION_DATE_KEY]!![0].toLong()
            Timber.i(
                "$localTag version = $version, " +
                        "created on : ${Utils.getFormattedDate(Date(creationDate))}"
            )
            recordTime("file read to map object")
            try {
                startRestore()
            } catch (badPaddingException: BadPaddingException) {
                Timber.e(badPaddingException, "wrong password entered for restore")
                Firebase.analytics.logEvent(AnalyticsKeys.RESTORE_FAILED) {
                    param(AnalyticsKeys.VERSION, version.toDouble())
                }
                return Result.failure()
            }
        }

        return Result.success()
    }

    private fun startRestore() {
        salt = importMap[CommonConstants.SALT_KEY]!!
        iv = importMap[CommonConstants.IV_KEY]!!
        recordTime("read salt and iv")
        val loginData = decryptLoginData(importMap[CommonConstants.LOGIN_DATA_KEY])
        val bankAccountData =
            decryptBankAccountData(importMap[CommonConstants.BANK_ACCOUNT_DATA_KEY])
        val bankCardData = decryptBankCardData(importMap[CommonConstants.BANK_CARD_DATA_KEY])
        val secureNoteData = decryptSecureNoteData(importMap[CommonConstants.SECURE_NOTE_DATA_KEY])
        recordTime("all data decrypted")

        restoreDataToDb(loginData, bankAccountData, bankCardData, secureNoteData)
        Firebase.analytics.logEvent(AnalyticsKeys.RESTORE_SUCCESS, null)
    }

    private fun decryptLoginData(loginDataByteArray: ByteArray?): List<ExportLoginData>? {
        return if (loginDataByteArray != null) {
            val json = String(
                passwordBasedEncryption.encryptDecrypt(
                    symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                    loginDataByteArray,
                    salt,
                    iv,
                    false
                )
            )
            Json.decodeFromString(ListSerializer(ExportLoginData.serializer()), json)
        } else {
            null
        }
    }

    private fun decryptBankAccountData(bankAccountDataByteArray: ByteArray?): List<ExportBankAccountData>? {
        return if (bankAccountDataByteArray != null) {
            val json = String(
                passwordBasedEncryption.encryptDecrypt(
                    symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                    bankAccountDataByteArray,
                    salt,
                    iv,
                    false
                )
            )
            Json.decodeFromString(ListSerializer(ExportBankAccountData.serializer()), json)
        } else {
            null
        }
    }

    private fun decryptBankCardData(bankCardDataByteArray: ByteArray?): List<ExportBankCardData>? {
        return if (bankCardDataByteArray != null) {
            val json = String(
                passwordBasedEncryption.encryptDecrypt(
                    symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                    bankCardDataByteArray,
                    salt,
                    iv,
                    false
                )
            )
            Json.decodeFromString(ListSerializer(ExportBankCardData.serializer()), json)
        } else {
            null
        }
    }

    private fun decryptSecureNoteData(secureNoteDataByteArray: ByteArray?): List<ExportSecureNoteData>? {
        return if (secureNoteDataByteArray != null) {
            val json = String(
                passwordBasedEncryption.encryptDecrypt(
                    symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                    secureNoteDataByteArray,
                    salt,
                    iv,
                    false
                )
            )
            Json.decodeFromString(ListSerializer(ExportSecureNoteData.serializer()), json)
        } else {
            null
        }
    }

    private fun restoreDataToDb(
        loginData: List<ExportLoginData>?,
        bankAccountData: List<ExportBankAccountData>?,
        bankCardData: List<ExportBankCardData>?,
        secureNoteData: List<ExportSecureNoteData>?
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
        }
    }

    private fun recordTime(message: String) {
        val timeTook = System.currentTimeMillis() - startTime
        val sec = CommonConstants.TIME_1_SECOND
        Timber.i("$localTag  $message : time took = $timeTook millis, ${timeTook / sec} sec")
        startTime = System.currentTimeMillis()
    }
}