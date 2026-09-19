package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import com.andryoga.safebox.e2e.E2ETestUtils
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore.RestoreFailureReason
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
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

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
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

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

    private suspend fun runBackupWorker(password: String = testPassword): Result {
        val worker = TestListenableWorkerBuilder<BackupDataWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(
                        CommonConstants.BACKUP_PARAM_PASSWORD,
                        symmetricKeyUtils.encrypt(password)
                    )
                    .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
                    .build()
            )
            .setWorkerFactory(workerFactory)
            .build()
        return worker.doWork()
    }

    private suspend fun runRestoreWorker(password: String = testPassword, fileUri: String): Result {
        val worker = TestListenableWorkerBuilder<RestoreDataWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(
                        CommonConstants.RESTORE_PARAM_PASSWORD,
                        symmetricKeyUtils.encrypt(password)
                    )
                    .putString(
                        CommonConstants.RESTORE_PARAM_FILE_URI,
                        fileUri
                    )
                    .build()
            )
            .setWorkerFactory(workerFactory)
            .build()
        return worker.doWork()
    }

    @Test
    fun exportToBackupFile_withValidPasswordAndSeededRecords_shouldCreateEncryptedBakFileAndReturnSuccess() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc01")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            val result = runBackupWorker()
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
                secureNoteDataRepository,
                authenticatorDataRepository
            )

            val backupDir = File(context.cacheDir, "backup_tc02")
            backupDir.deleteRecursively()
            backupDir.mkdirs()

            backupMetadataRepository.insertBackupMetadata(Uri.fromFile(backupDir))

            assertThat(runBackupWorker()).isEqualTo(Result.success())

            val generatedFile = backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && (f.name.endsWith(".bak") || f.name.endsWith(".bak.bin") || f.name.contains(
                    ".bak"
                ))
            }?.firstOrNull()
            assertThat(generatedFile).isNotNull()

            // Clear all tables to simulate a clean app state ready for restore
            safeBoxDatabase.clearAllTables()
            assertThat(loginDataRepository.getAllLoginData().first().isEmpty()).isTrue()

            val result = runRestoreWorker(testPassword, Uri.fromFile(generatedFile).toString())
            assertThat(result).isEqualTo(Result.success())
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

            val restoredAuthenticators = authenticatorDataRepository.getAllAuthenticatorData().first()
            assertThat(restoredAuthenticators.any {
                it.title == "GitHub Authenticator" &&
                    it.secretKey == E2ETestUtils.TEST_TOTP_SECRET_KEY
            }).isTrue()
        }

    @Test
    fun restoreFromBackupFile_withIncorrectPassword_shouldCatchBadPaddingExceptionAndReturnFailure() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository
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
            assertThat(result).isEqualTo(Result.failure(RestoreFailureReason.INCORRECT_PASSWORD.toWorkData()))
            assertThat(RestoreFailureReason.fromWorkData(result.outputData))
                .isEqualTo(RestoreFailureReason.INCORRECT_PASSWORD)
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
            assertThat(result).isEqualTo(Result.failure(RestoreFailureReason.CORRUPT_OR_INVALID_FILE.toWorkData()))
            assertThat(RestoreFailureReason.fromWorkData(result.outputData))
                .isEqualTo(RestoreFailureReason.CORRUPT_OR_INVALID_FILE)
        }

    @Test
    fun maximumBackupFileRotation_whenThresholdExceeded_shouldDeleteOldestBackupFiles() =
        runBlocking {
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository
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

    @Test
    fun restoreFromBackup_randomizedRecordsAllTypes_shouldRestore100PercentFieldEquality() {
        runBlocking {
            // Pinned rather than wall clock derived: this assertion compares a whole round trip,
            // so a run that goes red is only diagnosable if the exact record set can be recreated.
            val seed = RANDOMISED_RECORD_SEED
            val random = Random(seed)
            Timber.i("Running restoreFromBackup_randomizedRecordsAllTypes with SEED: $seed")

            safeBoxDatabase.clearAllTables()

            val expectedLogins: List<LoginData> = (1..5).map { createRandomLoginData(it, random) }
            val expectedCards: List<CardData> = (1..5).map { createRandomCardData(it, random) }
            val expectedAccounts: List<BankAccountData> =
                (1..5).map { createRandomBankAccountData(it, random) }
            val expectedNotes: List<NoteData> = (1..5).map { createRandomNoteData(it, random) }
            val expectedAuthenticators: List<AuthenticatorData> =
                (1..5).map { createRandomAuthenticatorData(it, random) }

            for (item in expectedLogins) {
                loginDataRepository.upsertLoginData(item)
            }
            for (item in expectedCards) {
                bankCardDataRepository.upsertBankCardData(item)
            }
            for (item in expectedAccounts) {
                bankAccountDataRepository.upsertBankAccountData(item)
            }
            for (item in expectedNotes) {
                secureNoteDataRepository.upsertSecureNoteData(item)
            }
            for (item in expectedAuthenticators) {
                authenticatorDataRepository.upsertAuthenticatorData(item)
            }

            val initialLogins = loginDataRepository.getAllLoginData().first()
                .map { loginDataRepository.getLoginDataByKey(it.key) }
            val initialCards = bankCardDataRepository.getAllBankCardData().first()
                .map { bankCardDataRepository.getBankCardDataByKey(it.key) }
            val initialAccounts = bankAccountDataRepository.getAllBankAccountData().first()
                .map { bankAccountDataRepository.getBankAccountDataByKey(it.key) }
            val initialNotes = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { secureNoteDataRepository.getSecureNoteDataByKey(it.key) }
            val initialAuthenticators = authenticatorDataRepository.getAllAuthenticatorData().first()
                .map { authenticatorDataRepository.getAuthenticatorDataByKey(it.key) }

            val backupDir = File(context.cacheDir, "backup_random_all")
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
                .map { loginDataRepository.getLoginDataByKey(it.key) }
            val restoredCards = bankCardDataRepository.getAllBankCardData().first()
                .map { bankCardDataRepository.getBankCardDataByKey(it.key) }
            val restoredAccounts = bankAccountDataRepository.getAllBankAccountData().first()
                .map { bankAccountDataRepository.getBankAccountDataByKey(it.key) }
            val restoredNotes = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { secureNoteDataRepository.getSecureNoteDataByKey(it.key) }
            val restoredAuthenticators =
                authenticatorDataRepository.getAllAuthenticatorData().first()
                    .map { authenticatorDataRepository.getAuthenticatorDataByKey(it.key) }

            assertThat(restoredLogins.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialLogins.map { it.copy(id = 0) })
            assertThat(restoredCards.map { it.copy(id = 0) }).containsExactlyElementsIn(initialCards.map {
                it.copy(
                    id = 0
                )
            })
            assertThat(restoredAccounts.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialAccounts.map { it.copy(id = 0) })
            assertThat(restoredNotes.map { it.copy(id = 0) }).containsExactlyElementsIn(initialNotes.map {
                it.copy(
                    id = 0
                )
            })
            // TotpConfig equality covers the algorithm, digit count and period, which the backup
            // file carries as text. A code generated from a restored record is only correct if all
            // four values survive together.
            assertThat(restoredAuthenticators.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialAuthenticators.map { it.copy(id = 0) })
        }
    }

    @Test
    fun restoreFromBackup_sparseCategories_whenOnlyLoginsExist_shouldRestoreLoginsAndHandleEmptyCategories() {
        runBlocking {
            val seed = System.currentTimeMillis()
            val random = Random(seed)
            Timber.i("Running restoreFromBackup_sparseCategories with SEED: $seed")

            safeBoxDatabase.clearAllTables()

            val expectedLogins: List<LoginData> = (1..4).map { createRandomLoginData(it, random) }
            for (item in expectedLogins) {
                loginDataRepository.upsertLoginData(item)
            }

            val initialLogins = loginDataRepository.getAllLoginData().first()
                .map { loginDataRepository.getLoginDataByKey(it.key) }

            val backupDir = File(context.cacheDir, "backup_sparse")
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
                .map { loginDataRepository.getLoginDataByKey(it.key) }
            val restoredCards = bankCardDataRepository.getAllBankCardData().first()
            val restoredAccounts = bankAccountDataRepository.getAllBankAccountData().first()
            val restoredNotes = secureNoteDataRepository.getAllSecureNoteData().first()

            assertThat(restoredLogins.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialLogins.map { it.copy(id = 0) })
            assertThat(restoredCards).isEmpty()
            assertThat(restoredAccounts).isEmpty()
            assertThat(restoredNotes).isEmpty()
        }
    }

    @Test
    fun restoreFromBackup_randomizedBulkVolume_shouldRestoreLargeDatasetAccurately() {
        runBlocking {
            val seed = System.currentTimeMillis()
            val random = Random(seed)
            Timber.i("Running restoreFromBackup_randomizedBulkVolume with SEED: $seed")

            safeBoxDatabase.clearAllTables()

            val expectedLogins: List<LoginData> = (1..50).map { createRandomLoginData(it, random) }
            val expectedCards: List<CardData> = (1..50).map { createRandomCardData(it, random) }
            val expectedAccounts: List<BankAccountData> =
                (1..50).map { createRandomBankAccountData(it, random) }
            val expectedNotes: List<NoteData> = (1..50).map { createRandomNoteData(it, random) }

            for (item in expectedLogins) {
                loginDataRepository.upsertLoginData(item)
            }
            for (item in expectedCards) {
                bankCardDataRepository.upsertBankCardData(item)
            }
            for (item in expectedAccounts) {
                bankAccountDataRepository.upsertBankAccountData(item)
            }
            for (item in expectedNotes) {
                secureNoteDataRepository.upsertSecureNoteData(item)
            }

            val initialLogins = loginDataRepository.getAllLoginData().first()
                .map { loginDataRepository.getLoginDataByKey(it.key) }
            val initialCards = bankCardDataRepository.getAllBankCardData().first()
                .map { bankCardDataRepository.getBankCardDataByKey(it.key) }
            val initialAccounts = bankAccountDataRepository.getAllBankAccountData().first()
                .map { bankAccountDataRepository.getBankAccountDataByKey(it.key) }
            val initialNotes = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { secureNoteDataRepository.getSecureNoteDataByKey(it.key) }

            val backupDir = File(context.cacheDir, "backup_bulk")
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
                .map { loginDataRepository.getLoginDataByKey(it.key) }
            val restoredCards = bankCardDataRepository.getAllBankCardData().first()
                .map { bankCardDataRepository.getBankCardDataByKey(it.key) }
            val restoredAccounts = bankAccountDataRepository.getAllBankAccountData().first()
                .map { bankAccountDataRepository.getBankAccountDataByKey(it.key) }
            val restoredNotes = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { secureNoteDataRepository.getSecureNoteDataByKey(it.key) }

            assertThat(restoredLogins.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialLogins.map { it.copy(id = 0) })
            assertThat(restoredCards.map { it.copy(id = 0) }).containsExactlyElementsIn(initialCards.map {
                it.copy(
                    id = 0
                )
            })
            assertThat(restoredAccounts.map { it.copy(id = 0) }).containsExactlyElementsIn(
                initialAccounts.map { it.copy(id = 0) })
            assertThat(restoredNotes.map { it.copy(id = 0) }).containsExactlyElementsIn(initialNotes.map {
                it.copy(
                    id = 0
                )
            })
        }
    }

    private fun randomFrom(pool: String, length: Int, random: Random): String {
        val codePoints = pool.codePoints().toArray()
        return (1..length).joinToString("") {
            String(Character.toChars(codePoints[random.nextInt(codePoints.size)]))
        }
    }

    private fun createRandomLoginData(id: Int, random: Random): LoginData {
        val idPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val notePool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ \n\t🔑🔐"
        val textPool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ 🔑🔐"
        return LoginData(
            id = id,
            title = "Login_${id}_${randomFrom(textPool, 6, random)}",
            url = if (random.nextBoolean()) "https://${
                randomFrom(
                    idPool,
                    8,
                    random
                )
            }.example.com" else null,
            userId = "user_${id}_${randomFrom(idPool, 5, random)}@test.com",
            password = "Pass_${id}_${randomFrom(textPool, 10, random)}",
            notes = if (random.nextBoolean()) "Notes\nLine 2\n${
                randomFrom(
                    notePool,
                    20,
                    random
                )
            }" else null,
            creationDate = Date(1700000000000L + random.nextInt(1000000)),
            updateDate = Date(1700000000000L + random.nextInt(1000000))
        )
    }

    private fun createRandomCardData(id: Int, random: Random): CardData {
        val notePool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ \n🔑🔐"
        val textPool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ 🔑🔐"
        return CardData(
            id = id,
            title = "Card_${id}_${randomFrom(textPool, 6, random)}",
            name = "Holder_${id}_${randomFrom(textPool, 5, random)}",
            number = (1..16).map { random.nextInt(10).toString() }.joinToString(""),
            expiryDate = String.format(
                java.util.Locale.ROOT,
                "%02d%02d",
                random.nextInt(12) + 1,
                random.nextInt(10) + 25
            ),
            cvv = String.format(java.util.Locale.ROOT, "%03d", random.nextInt(1000)),
            pin = if (random.nextBoolean()) String.format(
                java.util.Locale.ROOT,
                "%04d",
                random.nextInt(10000)
            ) else null,
            notes = if (random.nextBoolean()) "Card Note\n${
                randomFrom(
                    notePool,
                    15,
                    random
                )
            }" else null,
            creationDate = Date(1700000000000L + random.nextInt(1000000)),
            updateDate = Date(1700000000000L + random.nextInt(1000000))
        )
    }

    private fun createRandomBankAccountData(id: Int, random: Random): BankAccountData {
        val idPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val notePool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ \n🔑"
        val textPool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ 🔑"
        return BankAccountData(
            id = id,
            title = "Account_${id}_${randomFrom(textPool, 6, random)}",
            accountNo = (1..12).map { random.nextInt(10).toString() }.joinToString(""),
            customerName = "Cust_${id}_${randomFrom(textPool, 5, random)}",
            customerId = if (random.nextBoolean()) "CUST_${id}_${
                randomFrom(
                    idPool,
                    4,
                    random
                )
            }" else null,
            branchCode = if (random.nextBoolean()) "BR_${randomFrom(idPool, 4, random)}" else null,
            branchName = if (random.nextBoolean()) "Branch_${
                randomFrom(
                    textPool,
                    6,
                    random
                )
            }" else null,
            branchAddress = if (random.nextBoolean()) "Addr\nLine 1\n${
                randomFrom(
                    notePool,
                    15,
                    random
                )
            }" else null,
            ifscCode = if (random.nextBoolean()) "IFSC${randomFrom(idPool, 6, random)}" else null,
            micrCode = if (random.nextBoolean()) "MICR${randomFrom(idPool, 6, random)}" else null,
            notes = if (random.nextBoolean()) "Bank Note\n${
                randomFrom(
                    notePool,
                    15,
                    random
                )
            }" else null,
            creationDate = Date(1700000000000L + random.nextInt(1000000)),
            updateDate = Date(1700000000000L + random.nextInt(1000000))
        )
    }

    private fun createRandomNoteData(id: Int, random: Random): NoteData {
        val notePool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ \n\t🔑🔐"
        val textPool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ 🔑🔐"
        return NoteData(
            id = id,
            title = "Note_${id}_${randomFrom(textPool, 6, random)}",
            notes = "Note Content ${id}\nLine 2: ${
                randomFrom(
                    notePool,
                    30,
                    random
                )
            }\nLine 3: ${randomFrom(notePool, 20, random)}",
            creationDate = Date(1700000000000L + random.nextInt(1000000)),
            updateDate = Date(1700000000000L + random.nextInt(1000000))
        )
    }

    /**
     * Builds an authenticator record whose every generation parameter is randomised.
     *
     * The seed is drawn from the RFC 4648 Base32 alphabet and given a length the encoding can
     * actually produce, so the record is one the app would accept. Length matters as much as the
     * alphabet here: Base32Utils.isValidBase32 rejects a seed whose length leaves 1, 3 or 6
     * characters over a full quantum, and [RestoreDataWorker] then drops such a record on restore
     * by design, which would fail the round trip assertion for a reason that is not a round trip
     * defect. The algorithm, digit count and period all vary so a round trip that silently
     * defaulted any of them would fail the equality assertion.
     *
     * @param id Stable identifier, also used to keep titles unique.
     * @param random Seeded source so a failing run can be reproduced from the logged seed.
     * @return Randomised authenticator record.
     */
    private fun createRandomAuthenticatorData(id: Int, random: Random): AuthenticatorData {
        val base32Pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val textPool =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/ 🔑🔐"
        return AuthenticatorData(
            id = id,
            title = "Authenticator_${id}_${randomFrom(textPool, 6, random)}",
            config = TotpConfig(
                secretKey = randomFrom(base32Pool, REPRESENTABLE_SEED_LENGTHS.random(random), random),
                algorithm = TotpAlgorithm.entries[random.nextInt(TotpAlgorithm.entries.size)],
                digits = TotpDefaults.SUPPORTED_DIGITS.random(random),
                period = 15 + random.nextInt(46),
            ),
            creationDate = Date(1700000000000L + random.nextInt(1000000)),
            updateDate = Date(1700000000000L + random.nextInt(1000000))
        )
    }

    private companion object {
        // Any fixed value works, this one is arbitrary. What matters is that it does not change
        // between runs, so a failure reported from CI can be reproduced locally.
        private const val RANDOMISED_RECORD_SEED = 20260919L

        // A Base32 quantum is 8 characters and a trailing partial group can only be 2, 4, 5 or 7
        // of them, so a length leaving 1, 3 or 6 over is one no encoder could ever emit.
        private val REPRESENTABLE_SEED_LENGTHS = (16..32).filter { it % 8 !in setOf(1, 3, 6) }
    }
}
