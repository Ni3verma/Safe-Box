package com.andryoga.safebox.worker

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakeSymmetricKeyUtils
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.nio.file.Files
import java.util.Locale

class BackupDataWorkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK
    lateinit var loginDataDaoSecure: LoginDataDaoSecure

    @MockK
    lateinit var bankAccountDataDaoSecure: BankAccountDataDaoSecure

    @MockK
    lateinit var bankCardDataDaoSecure: BankCardDataDaoSecure

    @MockK
    lateinit var secureNoteDataDaoSecure: SecureNoteDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var fakeSymmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var fakePasswordBasedEncryption: PasswordBasedEncryption
    private lateinit var fakeBackupMetadataRepo: FakeBackupMetadataRepository
    private lateinit var tempDir: File
    private lateinit var mockFileUri: Uri

    private class FakeBackupMetadataRepository : BackupMetadataRepository {
        var metadata: BackupPathData? = null
        var updatedDate: Long? = null
        var deleted: Boolean = false

        override suspend fun insertBackupMetadata(uriPath: Uri?): Boolean {
            metadata = uriPath?.let { BackupPathData(it.toString(), it.path ?: "", "Just now") }
            return true
        }

        override suspend fun deleteBackupMetadata() {
            deleted = true
            metadata = null
        }

        override suspend fun updateLastBackupDate(date: Long) {
            updatedDate = date
        }

        override suspend fun isBackupPathSet(): Boolean = metadata != null

        override fun getBackupMetadata(): Flow<BackupPathData?> = flowOf(metadata)
    }

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        fakeSymmetricKeyUtils = FakeSymmetricKeyUtils()
        fakeBackupMetadataRepo = FakeBackupMetadataRepository()
        tempDir = Files.createTempDirectory("backup_worker_unit_test").toFile()

        every { context.applicationContext } returns context
        every { context.getString(any()) } returns "Backup Notification"
        every {
            context.checkPermission(
                any(),
                any(),
                any()
            )
        } returns PackageManager.PERMISSION_DENIED
        every { context.checkCallingOrSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED

        fakePasswordBasedEncryption = object : PasswordBasedEncryption {
            override fun encryptDecrypt(
                password: CharArray,
                data: ByteArray,
                salt: ByteArray,
                iv: ByteArray,
                encrypt: Boolean
            ): ByteArray = data.copyOf()

            override fun getRandomSalt(): ByteArray = ByteArray(16) { 1 }
            override fun getRandomIV(): ByteArray = ByteArray(16) { 2 }
        }

        mockFileUri = mockk(relaxed = true)
        every { mockFileUri.scheme } returns "file"
        every { mockFileUri.path } answers { tempDir.absolutePath }
        every { mockFileUri.toString() } answers { "file://${tempDir.absolutePath}" }

        mockkStatic(Uri::class)
        every { Uri.parse(match { it.startsWith("file://") || it.startsWith("content://") }) } returns mockFileUri
        every { Uri.fromFile(match { it.absolutePath.startsWith(tempDir.absolutePath) }) } returns mockFileUri
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        tempDir.deleteRecursively()
    }

    private fun buildWorker(inputData: Data = Data.EMPTY): BackupDataWorker {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return BackupDataWorker(
                    context = appContext,
                    params = workerParameters,
                    symmetricKeyUtils = fakeSymmetricKeyUtils,
                    backupMetadataRepository = fakeBackupMetadataRepo,
                    passwordBasedEncryption = fakePasswordBasedEncryption,
                    loginDataDaoSecure = loginDataDaoSecure,
                    bankAccountDataDaoSecure = bankAccountDataDaoSecure,
                    bankCardDataDaoSecure = bankCardDataDaoSecure,
                    secureNoteDataDaoSecure = secureNoteDataDaoSecure,
                    analyticsHelper = analyticsHelper,
                    dispatchersProvider = mainDispatcherRule.testDispatcherProvider
                )
            }
        }

        return TestListenableWorkerBuilder<BackupDataWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }

    @Test
    fun doWork_whenNoBackupMetadata_completesSuccessWithoutWriting() = runTest {
        fakeBackupMetadataRepo.metadata = null

        val worker = buildWorker()
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(fakeBackupMetadataRepo.updatedDate).isNull()

        val createdFiles =
            tempDir.listFiles { f -> f.name.startsWith("SafeBoxBackup") } ?: emptyArray()
        assertThat(createdFiles).isEmpty()

        coVerify(exactly = 0) {
            loginDataDaoSecure.exportAllData()
            bankAccountDataDaoSecure.exportAllData()
            bankCardDataDaoSecure.exportAllData()
            secureNoteDataDaoSecure.exportAllData()
        }
    }

    @Test
    fun doWork_whenDatabaseHasRecords_createsEncryptedBackupAndUpdatesTimestamp() = runTest {
        fakeBackupMetadataRepo.metadata = BackupPathData(
            uriString = "file://${tempDir.absolutePath}",
            path = tempDir.absolutePath,
            lastBackupTime = "Just now"
        )

        coEvery { loginDataDaoSecure.exportAllData() } returns listOf(
            ExportLoginData("GitHub", "https://github.com", "secret", "notes", "user", 1000L, 1000L)
        )
        coEvery { bankAccountDataDaoSecure.exportAllData() } returns emptyList()
        coEvery { bankCardDataDaoSecure.exportAllData() } returns emptyList()
        coEvery { secureNoteDataDaoSecure.exportAllData() } returns emptyList()

        val inputData = Data.Builder()
            .putString(CommonConstants.BACKUP_PARAM_PASSWORD, "enc_password")
            .putBoolean(CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)
            .build()

        val worker = buildWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        assertThat(fakeBackupMetadataRepo.updatedDate).isNotNull()

        val createdFiles = tempDir.listFiles { f -> f.name.startsWith("SafeBoxBackup") }
        assertThat(createdFiles).isNotNull()
        assertThat(createdFiles!!.isNotEmpty()).isTrue()

        val backupFile = createdFiles.first()
        val backupMap = ObjectInputStream(FileInputStream(backupFile)).use {
            @Suppress("UNCHECKED_CAST")
            it.readObject() as Map<String, ByteArray?>
        }
        val loginDataBytes = backupMap[CommonConstants.LOGIN_DATA_KEY]
        assertThat(loginDataBytes).isNotNull()
        val restoredJson = String(
            fakePasswordBasedEncryption.encryptDecrypt(
                "enc_password".toCharArray(),
                loginDataBytes!!,
                ByteArray(0),
                ByteArray(0),
                false
            ),
            Charsets.UTF_8
        )
        val restoredList = Json.decodeFromString(
            ListSerializer(ExportLoginData.serializer()),
            restoredJson
        )
        assertThat(restoredList).hasSize(1)
        assertThat(restoredList[0].title).isEqualTo("GitHub")
        assertThat(restoredList[0].url).isEqualTo("https://github.com")
        assertThat(restoredList[0].password).isEqualTo("secret")
    }

    @Test
    fun doWork_whenExtraBackupsExist_deletesOldestFilesExceedingLimit() = runTest {
        for (i in 1..(CommonConstants.MAX_BACKUP_FILES + 2)) {
            val formatted = String.format(Locale.ROOT, "%02d", i)
            val dummyFile = File(tempDir, "SafeBoxBackup202601010000000$formatted.bak")
            dummyFile.writeText("dummy backup payload $i")
        }

        fakeBackupMetadataRepo.metadata = BackupPathData(
            uriString = "file://${tempDir.absolutePath}",
            path = tempDir.absolutePath,
            lastBackupTime = "Just now"
        )

        coEvery { loginDataDaoSecure.exportAllData() } returns listOf(
            ExportLoginData("GitHub", "https://github.com", "secret", "notes", "user", 1000L, 1000L)
        )
        coEvery { bankAccountDataDaoSecure.exportAllData() } returns emptyList()
        coEvery { bankCardDataDaoSecure.exportAllData() } returns emptyList()
        coEvery { secureNoteDataDaoSecure.exportAllData() } returns emptyList()

        val inputData = Data.Builder()
            .putString(CommonConstants.BACKUP_PARAM_PASSWORD, "enc_password")
            .build()

        val worker = buildWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())

        val oldestFile = File(tempDir, "SafeBoxBackup20260101000000001.bak")
        assertThat(oldestFile.exists()).isFalse()

        val remainingFiles =
            tempDir.listFiles { f -> f.name.startsWith("SafeBoxBackup") } ?: emptyArray()
        assertThat(remainingFiles.size).isEqualTo(CommonConstants.MAX_BACKUP_FILES)

        val newerFile = File(
            tempDir,
            "SafeBoxBackup202601010000000${
                String.format(
                    Locale.ROOT,
                    "%02d",
                    CommonConstants.MAX_BACKUP_FILES + 2
                )
            }.bak"
        )
        assertThat(newerFile.exists()).isTrue()
    }

    @Test
    fun doWork_whenMissingPasswordInput_failsAndDeletesBackupMetadata() = runTest {
        fakeBackupMetadataRepo.metadata = BackupPathData(
            uriString = "file://${tempDir.absolutePath}",
            path = tempDir.absolutePath,
            lastBackupTime = "Just now"
        )

        val worker = buildWorker(Data.EMPTY)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(fakeBackupMetadataRepo.deleted).isTrue()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.BACKUP_DATA_FAILURE)).isTrue()
    }
}
