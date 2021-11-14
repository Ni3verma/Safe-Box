package com.andryoga.safebox.worker
//
// import android.content.Context
// import android.net.Uri
// import androidx.hilt.work.HiltWorker
// import androidx.work.CoroutineWorker
// import androidx.work.WorkerParameters
// import com.andryoga.safebox.common.Constants
// import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
// import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
// import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
// import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
// import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
// import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
// import dagger.assisted.Assisted
// import dagger.assisted.AssistedInject
// import kotlinx.coroutines.ExperimentalCoroutinesApi
// import timber.log.Timber
// import java.io.ObjectInputStream
//
// @HiltWorker
// @ExperimentalCoroutinesApi
// class RestoreDataWorker @AssistedInject constructor(
//    @Assisted context: Context,
//    @Assisted params: WorkerParameters,
//    private val symmetricKeyUtils: SymmetricKeyUtils,
//    private val passwordBasedEncryption: PasswordBasedEncryption,
//    private val loginDataDaoSecure: LoginDataDaoSecure,
//    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
//    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
//    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
// ) : CoroutineWorker(context, params) {
//
//    private val localTag = "restore data worker -> "
//
//    private var startTime = System.currentTimeMillis()
//
//    private lateinit var salt: ByteArray
//    private lateinit var iv: ByteArray
//
//    override suspend fun doWork(): Result {
//        startTime = System.currentTimeMillis()
//        val inputPassword = inputData.getString(Constants.RESTORE_PARAM_PASSWORD)
//            ?: throw IllegalArgumentException("expected password input was not received")
//        val fileUri = inputData.getString(Constants.RESTORE_PARAM_FILE_URI)
//            ?: throw IllegalArgumentException("expected file uri input was not received")
//
//        ObjectInputStream(
//            applicationContext.contentResolver.openInputStream(Uri.parse(fileUri))
//        ).use {
//            val a: Map<String, ByteArray> = it.readObject() as Map<String, ByteArray>
//            Timber.d("$a")
//        }
//
//        return Result.success()
//    }
// }
