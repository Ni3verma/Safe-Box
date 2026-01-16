package com.andryoga.safebox.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import javax.inject.Inject

/**
 * investigate why @HiltWorker is not working. if it starts to work, then below boiler plate code is not required
 */
class SafeBoxWorkerFactory @Inject constructor(
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val backupMetadataRepository: BackupMetadataRepository,
    private val passwordBasedEncryption: PasswordBasedEncryption,
    private val loginDataDaoSecure: LoginDataDaoSecure,
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure,
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure,
    private val safeBoxDatabase: SafeBoxDatabase,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            BackupDataWorker::class.java.name -> {
                BackupDataWorker(
                    appContext,
                    workerParameters,
                    symmetricKeyUtils,
                    backupMetadataRepository,
                    passwordBasedEncryption,
                    loginDataDaoSecure,
                    bankAccountDataDaoSecure,
                    bankCardDataDaoSecure,
                    secureNoteDataDaoSecure
                )
            }

            RestoreDataWorker::class.java.name -> {
                RestoreDataWorker(
                    appContext,
                    workerParameters,
                    symmetricKeyUtils,
                    passwordBasedEncryption,
                    safeBoxDatabase,
                    loginDataDaoSecure,
                    bankAccountDataDaoSecure,
                    bankCardDataDaoSecure,
                    secureNoteDataDaoSecure
                )
            }

            else -> {
                null
            }
        }
    }
}
