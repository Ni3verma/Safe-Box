package com.andryoga.safebox.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.common.Constants
import com.andryoga.safebox.common.Utils.getFormattedDate
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.util.*

@HiltWorker
@ExperimentalCoroutinesApi
class RestoreDataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
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
        inputPassword = inputData.getString(Constants.RESTORE_PARAM_PASSWORD)
            ?: throw IllegalArgumentException("expected password input was not received")
        val fileUri = inputData.getString(Constants.RESTORE_PARAM_FILE_URI)
            ?: throw IllegalArgumentException("expected file uri input was not received")

        ObjectInputStream(
            applicationContext.contentResolver.openInputStream(Uri.parse(fileUri))
        ).use {
            val fileObject = it.readObject()
            if (fileObject !is Map<*, *>)
                throw InvalidObjectException("input file is not correct, was not able to read it is as Map")

            importMap = fileObject as Map<String, ByteArray?>
            val version = importMap[Constants.VERSION_KEY]!![0].toInt()
            val creationDate = importMap[Constants.CREATION_DATE_KEY]!![0].toLong()
            Timber.i(
                "$localTag version = $version, created on : ${
                getFormattedDate(
                    Date(
                        creationDate
                    )
                )
                }"
            )

            startRestore()
        }

        return Result.success()
    }

    private fun startRestore() {
        salt = importMap[Constants.SALT_KEY]!!
        iv = importMap[Constants.IV_KEY]!!
        val loginData = decryptLoginData(importMap[Constants.LOGIN_DATA_KEY])
        val bankAccountData = decryptBankAccountData(importMap[Constants.BANK_ACCOUNT_DATA_KEY])
        val bankCardData = decryptBankCardData(importMap[Constants.BANK_CARD_DATA_KEY])
        val secureNoteData = decryptSecureNoteData(importMap[Constants.SECURE_NOTE_DATA_KEY])

        restoreDataToDb(loginData, bankAccountData, bankCardData, secureNoteData)
    }

    private fun decryptLoginData(loginDataByteArray: ByteArray?): List<ExportLoginData>? {
        return if (loginDataByteArray != null) {
            val json = String(
                passwordBasedEncryption.encryptDecrypt(
                    symmetricKeyUtils.decrypt(inputPassword).toCharArray(),
                    loginDataByteArray,
                    salt, iv, false
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
                    salt, iv, false
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
                    salt, iv, false
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
                    salt, iv, false
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
        Timber.d("${loginData?.size}, ${bankAccountData?.size}, ${bankCardData?.size}, ${secureNoteData?.size}")
        safeBoxDatabase.runInTransaction {
            Timber.i("restoring login data")
            loginDataDaoSecure.deleteAllData()
            loginData?.let {
                loginDataDaoSecure.insertMultipleLoginData(
                    loginData.map {
                        LoginDataEntity(
                            0, it.title, it.url, it.password, it.notes,
                            it.userId, Date(it.creationDate), Date(it.updateDate)
                        )
                    }
                )
            }

            Timber.i("restoring bank account data")
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

            Timber.i("restoring bank card data")
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

            Timber.i("restoring secure note data")
            secureNoteDataDaoSecure.deleteAllData()
            secureNoteData?.let {
                secureNoteDataDaoSecure.insertMultipleSecureNoteData(
                    secureNoteData.map {
                        SecureNoteDataEntity(
                            0, it.title, it.notes,
                            Date(it.creationDate), Date(it.updateDate)
                        )
                    }
                )
            }
        }
    }
}
