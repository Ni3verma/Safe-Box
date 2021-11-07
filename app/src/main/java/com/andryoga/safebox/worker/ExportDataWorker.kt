package com.andryoga.safebox.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.common.Constants
import com.andryoga.safebox.common.Constants.CREATION_DATE_KEY
import com.andryoga.safebox.common.Constants.EXPORT_IMPORT_VERSION
import com.andryoga.safebox.common.Constants.IV_KEY
import com.andryoga.safebox.common.Constants.SALT_KEY
import com.andryoga.safebox.common.Constants.VERSION_KEY
import com.andryoga.safebox.common.Constants.time1Sec
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
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
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import kotlin.properties.Delegates

@HiltWorker
@ExperimentalCoroutinesApi
class ExportDataWorker
@AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val passwordBasedEncryption: PasswordBasedEncryption,
    private val loginDataDaoSecure: LoginDataDaoSecure,
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : CoroutineWorker(context, params) {

    private var startTime by Delegates.notNull<Long>()

    private lateinit var salt: ByteArray
    private lateinit var iv: ByteArray

    override suspend fun doWork(): Result {

        startTime = System.currentTimeMillis()

        val inputPassword = inputData.getString("PASSWORD")
        val password = symmetricKeyUtils.decrypt(inputPassword!!)
        Timber.i("input pswrd = $inputPassword \ndecrypted password = $password")
        recordTime("decrypting password")

        val loginData = loginDataDaoSecure.exportAllData()
        val bankAccountData = bankAccountDataDaoSecure.exportAllData()
        val bankCardData = bankCardDataDaoSecure.exportAllData()
        val secureNoteData = secureNoteDataDaoSecure.exportAllData()

        recordTime("got all data")

        if (
            shouldExport(loginData, bankAccountData, bankCardData, secureNoteData)
        ) {
            Timber.i("data is present for export")
            salt = passwordBasedEncryption.getRandomSalt()
            iv = passwordBasedEncryption.getRandomIV()
            val exportMap = mutableMapOf<String, Any>(
                SALT_KEY to salt,
                IV_KEY to iv,
                VERSION_KEY to EXPORT_IMPORT_VERSION
            )
            recordTime("got salt and iv")

            val loginDataByteArray: ByteArray? = exportLoginData(loginData, inputPassword)
            recordTime("got login data byte array")
            if (loginDataByteArray != null) {
                exportMap[Constants.LOGIN_DATA_KEY] = loginDataByteArray
            }

            val bankAccountDataByteArray: ByteArray? =
                exportBankAccountData(bankAccountData, inputPassword)
            recordTime("got bank account data byte array")
            if (bankAccountDataByteArray != null) {
                exportMap[Constants.BANK_ACCOUNT_DATA_KEY] = bankAccountDataByteArray
            }

            val bankCardDataByteArray: ByteArray? = exportBankCardData(bankCardData, inputPassword)
            recordTime("got bank card data byte array")
            if (bankCardDataByteArray != null) {
                exportMap[Constants.BANK_CARD_DATA_KEY] = bankCardDataByteArray
            }

            val secureNoteDataByteArray: ByteArray? =
                exportSecureNoteData(secureNoteData, inputPassword)
            recordTime("got secure note data byte array")
            if (secureNoteDataByteArray != null) {
                exportMap[Constants.SECURE_NOTE_DATA_KEY] = secureNoteDataByteArray
            }

            exportMap[CREATION_DATE_KEY] = System.currentTimeMillis()

            ObjectOutputStream(FileOutputStream("nitinTestEnc")).use {
                it.writeObject(exportMap)
            }
            recordTime("exported to file")
        } else {
            Timber.i("nothing to export")
        }

        return Result.Success()
    }

    private fun exportLoginData(
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

    private fun exportBankAccountData(
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

    private fun exportBankCardData(
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

    private fun exportSecureNoteData(
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
        Timber.i("$message : time took = $timeTook millisec, ${timeTook / sec} sec")
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
}
