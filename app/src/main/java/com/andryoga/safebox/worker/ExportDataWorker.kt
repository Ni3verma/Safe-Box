package com.andryoga.safebox.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.common.Constants.time1Sec
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
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
            val salt = passwordBasedEncryption.getRandomSalt()
            val iv = passwordBasedEncryption.getRandomIV()
            recordTime("got salt and iv")
            Timber.i("$salt\n$iv")
        } else {
            Timber.i("nothing to export")
        }

        return Result.Success()
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
