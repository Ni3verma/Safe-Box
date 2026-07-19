package com.andryoga.safebox.worker

import android.content.Context
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
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import javax.crypto.BadPaddingException

class RestoreDataWorkerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxed = true)
    lateinit var context: Context

    @MockK
    lateinit var symmetricKeyUtils: SymmetricKeyUtils

    @MockK
    lateinit var passwordBasedEncryption: PasswordBasedEncryption

    @MockK
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @MockK(relaxUnitFun = true)
    lateinit var loginDataDaoSecure: LoginDataDaoSecure

    @MockK(relaxUnitFun = true)
    lateinit var bankAccountDataDaoSecure: BankAccountDataDaoSecure

    @MockK(relaxUnitFun = true)
    lateinit var bankCardDataDaoSecure: BankCardDataDaoSecure

    @MockK(relaxUnitFun = true)
    lateinit var secureNoteDataDaoSecure: SecureNoteDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        tempDir = Files.createTempDirectory("restore_worker_unit_test").toFile()

        every { safeBoxDatabase.runInTransaction(any<Runnable>()) } answers {
            firstArg<Runnable>().run()
        }

        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val uriString = firstArg<String>()
            val filePath =
                if (uriString.startsWith("file://")) uriString.removePrefix("file://") else uriString
            val mockUri = mockk<Uri>(relaxed = true)
            every { mockUri.scheme } returns if (uriString.startsWith("content")) "content" else "file"
            every { mockUri.path } returns filePath
            every { mockUri.toString() } returns uriString
            mockUri
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        tempDir.deleteRecursively()
    }

    private fun buildWorker(inputData: Data): RestoreDataWorker {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return RestoreDataWorker(
                    context = appContext,
                    params = workerParameters,
                    symmetricKeyUtils = symmetricKeyUtils,
                    passwordBasedEncryption = passwordBasedEncryption,
                    safeBoxDatabase = safeBoxDatabase,
                    loginDataDaoSecure = loginDataDaoSecure,
                    bankAccountDataDaoSecure = bankAccountDataDaoSecure,
                    bankCardDataDaoSecure = bankCardDataDaoSecure,
                    secureNoteDataDaoSecure = secureNoteDataDaoSecure,
                    analyticsHelper = analyticsHelper
                )
            }
        }

        return TestListenableWorkerBuilder<RestoreDataWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(workerFactory)
            .build()
    }

    @Test
    fun doWork_validBackupFileAndCorrectPassword_restoresDataWithinDatabaseTransaction() = runTest {
        val loginList = listOf(
            ExportLoginData(
                "GitHub Account",
                "https://github.com",
                "secret",
                "notes",
                "octocat",
                1000L,
                1000L
            )
        )
        val loginJson = Json.encodeToString(ListSerializer(ExportLoginData.serializer()), loginList)
        val dummyCipherBytes = ByteArray(32) { 7 }

        val backupMap = WorkerTestFixtures.createBackupMap(
            loginData = dummyCipherBytes
        )
        val backupFile = File(tempDir, "ValidBackup.bak")
        WorkerTestFixtures.writeBackupMapToFile(backupFile, backupMap)

        every { symmetricKeyUtils.decrypt("enc_password") } returns "raw_password"
        every {
            passwordBasedEncryption.encryptDecrypt(any(), dummyCipherBytes, any(), any(), false)
        } returns loginJson.toByteArray()

        val inputData = Data.Builder()
            .putString(CommonConstants.RESTORE_PARAM_PASSWORD, "enc_password")
            .putString(CommonConstants.RESTORE_PARAM_FILE_URI, "file://${backupFile.absolutePath}")
            .build()

        val worker = buildWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.success())
        verify(exactly = 1) { loginDataDaoSecure.deleteAllData() }
        verify(exactly = 1) { loginDataDaoSecure.insertMultipleLoginData(any()) }
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.RESTORE_DATA_SUCCESS)).isTrue()
    }

    @Test
    fun doWork_invalidPassword_throwsBadPaddingExceptionAndLogsFailureAnalytics() = runTest {
        val dummyCipherBytes = ByteArray(32) { 7 }
        val backupMap = WorkerTestFixtures.createBackupMap(loginData = dummyCipherBytes)
        val backupFile = File(tempDir, "WrongPasswordBackup.bak")
        WorkerTestFixtures.writeBackupMapToFile(backupFile, backupMap)

        every { symmetricKeyUtils.decrypt("wrong_enc_password") } returns "wrong_password"
        every {
            passwordBasedEncryption.encryptDecrypt(any(), dummyCipherBytes, any(), any(), false)
        } throws BadPaddingException("Invalid decryption key or password")

        val inputData = Data.Builder()
            .putString(CommonConstants.RESTORE_PARAM_PASSWORD, "wrong_enc_password")
            .putString(CommonConstants.RESTORE_PARAM_FILE_URI, "file://${backupFile.absolutePath}")
            .build()

        val worker = buildWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.RESTORE_DATA_FAILURE)).isTrue()
    }

    @Test
    fun doWork_corruptFile_failsAndLogsAnalytics() = runTest {
        val corruptFile = File(tempDir, "CorruptBackup.bak")
        corruptFile.writeText("This is not a serialized Map object stream.")

        every { symmetricKeyUtils.decrypt("enc_password") } returns "raw_password"

        val inputData = Data.Builder()
            .putString(CommonConstants.RESTORE_PARAM_PASSWORD, "enc_password")
            .putString(CommonConstants.RESTORE_PARAM_FILE_URI, "file://${corruptFile.absolutePath}")
            .build()

        val worker = buildWorker(inputData)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.RESTORE_DATA_FAILURE)).isTrue()
    }

    @Test
    fun doWork_missingInputParameters_failsAndLogsAnalytics() = runTest {
        val worker = buildWorker(Data.EMPTY)
        val result = worker.doWork()

        assertThat(result).isEqualTo(Result.failure())
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.RESTORE_DATA_FAILURE)).isTrue()
    }
}
