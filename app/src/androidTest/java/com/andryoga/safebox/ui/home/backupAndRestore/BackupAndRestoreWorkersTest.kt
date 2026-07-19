package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.e2e.E2ETestUtils
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.worker.BackupDataWorker
import com.andryoga.safebox.worker.RestoreDataWorker
import com.andryoga.safebox.worker.SafeBoxWorkerFactory
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

/**
 * Comprehensive Hilt Instrumented Test Suite verifying BackupDataWorker and RestoreDataWorker execution,
 * encryption/decryption integrity, database restoration, wrong password handling, corrupted payload validation,
 * and automatic file rotation threshold (TC_BACKUP_01 through TC_BACKUP_05).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupAndRestoreWorkersTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workerFactory: SafeBoxWorkerFactory

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @Inject
    lateinit var symmetricKeyUtils: SymmetricKeyUtils

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testPassword = "Qwerty@@123"

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun exportToBackupFile_withValidPasswordAndSeededRecords_shouldCreateEncryptedBakFileAndReturnSuccess() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc01")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            val worker = TestListenableWorkerBuilder<BackupDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.BACKUP_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()

            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())

            val bakFiles = backupDir.listFiles { file ->
                file.name.startsWith("SafeBoxBackup") && (file.name.endsWith(".bak") || file.name.endsWith(
                    ".bak.bin"
                ) || file.name.contains(".bak"))
            }
            assertThat(bakFiles).isNotNull()
            assertThat(bakFiles!!.isNotEmpty()).isTrue()
        }

    @Test
    fun restoreFromBackupFile_withValidPasswordAndEncryptedBakFile_shouldDecryptAndRestoreAllRecordsToDatabase() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc02")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            val backupWorker = TestListenableWorkerBuilder<BackupDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.BACKUP_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()
            assertThat(backupWorker.doWork()).isEqualTo(Result.success())

            val generatedFile = backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && (f.name.endsWith(".bak") || f.name.endsWith(".bak.bin") || f.name.contains(
                    ".bak"
                ))
            }?.firstOrNull()
            assertThat(generatedFile).isNotNull()

            // Clear all tables to simulate a clean app state ready for restore
            safeBoxDatabase.clearAllTables()
            assertThat(loginDataRepository.getAllLoginData().first().isEmpty()).isTrue()

            val restoreWorker = TestListenableWorkerBuilder<RestoreDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.RESTORE_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putString(
                            CommonConstants.RESTORE_PARAM_FILE_URI,
                            Uri.fromFile(generatedFile).toString()
                        )
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()

            val result = restoreWorker.doWork()
            assertThat(result).isEqualTo(Result.success())

            val restoredLogins = loginDataRepository.getAllLoginData().first()
            assertThat(restoredLogins.any { it.title == "Apple ID Login" && it.userId == "user@apple.com" }).isTrue()

            val restoredCards = bankCardDataRepository.getAllBankCardData().first()
            assertThat(restoredCards.any {
                it.title == "Chase Sapphire Card" && (it.number == "XXXXXXXXXXXX4444" || it.number.endsWith(
                    "4444"
                ))
            }).isTrue()

            val restoredAccounts = bankAccountDataRepository.getAllBankAccountData().first()
            assertThat(restoredAccounts.any { it.title == "Silicon Valley Checking" }).isTrue()

            val restoredNotes = secureNoteDataRepository.getAllSecureNoteData().first()
            assertThat(restoredNotes.any { it.title == "Wifi Router Secrets" }).isTrue()
        }

    @Test
    fun restoreFromBackupFile_withIncorrectPassword_shouldCatchBadPaddingExceptionAndReturnFailure() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc03")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            val backupWorker = TestListenableWorkerBuilder<BackupDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.BACKUP_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()
            assertThat(backupWorker.doWork()).isEqualTo(Result.success())

            val generatedFile = backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && (f.name.endsWith(".bak") || f.name.endsWith(".bak.bin") || f.name.contains(
                    ".bak"
                ))
            }?.firstOrNull()
            assertThat(generatedFile).isNotNull()

            safeBoxDatabase.clearAllTables()

            // Attempt restore using incorrect password
            val restoreWorker = TestListenableWorkerBuilder<RestoreDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.RESTORE_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt("WrongPassword999!")
                        )
                        .putString(
                            CommonConstants.RESTORE_PARAM_FILE_URI,
                            Uri.fromFile(generatedFile).toString()
                        )
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()

            val result = restoreWorker.doWork()
            assertThat(result).isEqualTo(Result.failure())
            assertThat(loginDataRepository.getAllLoginData().first().isEmpty()).isTrue()
        }

    @Test
    fun restoreFromBackupFile_withCorruptedOrInvalidPayload_shouldCatchExceptionAndReturnFailure() =
        runBlocking {
            val backupDir = File(context.cacheDir, "backup_tc04")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            val corruptedFile = File(backupDir, "corrupted_payload.bak")
            corruptedFile.writeText("This is not a serialized ObjectStream map!")

            val restoreWorker = TestListenableWorkerBuilder<RestoreDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.RESTORE_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putString(
                            CommonConstants.RESTORE_PARAM_FILE_URI,
                            Uri.fromFile(corruptedFile).toString()
                        )
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()

            val result = restoreWorker.doWork()
            assertThat(result).isEqualTo(Result.failure())
        }

    @Test
    fun maximumBackupFileRotation_whenThresholdExceeded_shouldDeleteOldestBackupFiles() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc05")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            // Pre-create 10 backup files (the maximum allowed threshold)
            for (i in 1..CommonConstants.MAX_BACKUP_FILES) {
                val formattedIndex = String.format(java.util.Locale.ROOT, "%02d", i)
                val dummyFile = File(backupDir, "SafeBoxBackup202601010000000$formattedIndex.bak")
                dummyFile.writeText("dummy content $i")
            }

            val initialFiles = backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && (f.name.endsWith(".bak") || f.name.endsWith(".bak.bin") || f.name.contains(
                    ".bak"
                ))
            }
            assertThat(initialFiles?.size).isEqualTo(CommonConstants.MAX_BACKUP_FILES)

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            // Run worker to create a new backup when threshold is reached
            val worker = TestListenableWorkerBuilder<BackupDataWorker>(context)
                .setInputData(
                    Data.Builder()
                        .putString(
                            CommonConstants.BACKUP_PARAM_PASSWORD,
                            symmetricKeyUtils.encrypt(testPassword)
                        )
                        .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                        .build()
                )
                .setWorkerFactory(workerFactory)
                .build()

            val result = worker.doWork()
            assertThat(result).isEqualTo(Result.success())

            val finalFiles = backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && (f.name.endsWith(".bak") || f.name.endsWith(".bak.bin") || f.name.contains(
                    ".bak"
                ))
            }
            assertThat(finalFiles).isNotNull()
            assertThat(finalFiles!!.size).isEqualTo(CommonConstants.MAX_BACKUP_FILES)
            // Verify the oldest file (SafeBoxBackup20260101000000001.bak) was deleted during rotation
            assertThat(finalFiles.any { it.name == "SafeBoxBackup20260101000000001.bak" }).isFalse()
        }
}
