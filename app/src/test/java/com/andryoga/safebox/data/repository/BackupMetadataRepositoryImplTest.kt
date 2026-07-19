@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class BackupMetadataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var context: Context

    @MockK
    lateinit var contentResolver: ContentResolver

    @MockK(relaxUnitFun = true)
    lateinit var backupMetadataDao: BackupMetadataDao

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: BackupMetadataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { context.contentResolver } returns contentResolver
        analyticsHelper = FakeAnalyticsHelper()
        repository = BackupMetadataRepositoryImpl(
            context = context,
            backupMetadataDao = backupMetadataDao,
            analyticsHelper = analyticsHelper
        )
    }

    @Test
    fun insertBackupMetadata_whenUriIsNull_shouldLogResultFalseAndSkipDbInsert() = runTest {
        repository.insertBackupMetadata(null)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.BACKUP_SELECT_DIR_RESULT)).isTrue()
        val event =
            analyticsHelper.loggedEvents.first { it.key == AnalyticsKey.BACKUP_SELECT_DIR_RESULT }
        assertThat(event.params[AnalyticsParam.RESULT.paramName]).isEqualTo(false)

        coVerify(exactly = 0) { backupMetadataDao.insertBackupMetadata(any()) }
    }

    @Test
    fun insertBackupMetadata_whenUriIsContentScheme_shouldTakePersistableUriPermissionAndInsertToDao() =
        runTest {
            val mockUri = mockk<Uri>()
            every { mockUri.scheme } returns "content"
            every { mockUri.path } returns "/tree/downloads"
            every { mockUri.toString() } returns "content://com.android.providers.downloads/tree/downloads"
            every { contentResolver.takePersistableUriPermission(mockUri, any()) } returns Unit

            repository.insertBackupMetadata(mockUri)

            verify(exactly = 1) { contentResolver.takePersistableUriPermission(mockUri, any()) }
            coVerify(exactly = 1) {
                backupMetadataDao.insertBackupMetadata(
                    match {
                        it.key == 1 &&
                                it.uriString == "content://com.android.providers.downloads/tree/downloads" &&
                                it.displayPath == "/tree/downloads" &&
                                it.lastBackupDate == null
                    }
                )
            }

            val event =
                analyticsHelper.loggedEvents.first { it.key == AnalyticsKey.BACKUP_SELECT_DIR_RESULT }
            assertThat(event.params[AnalyticsParam.RESULT.paramName]).isEqualTo(true)
        }

    @Test
    fun insertBackupMetadata_whenTakePersistableUriPermissionThrowsSecurityException_shouldCatchAndStillInsertToDao() =
        runTest {
            val mockUri = mockk<Uri>()
            every { mockUri.scheme } returns "content"
            every { mockUri.path } returns "/tree/downloads"
            every { mockUri.toString() } returns "content://com.android.providers.downloads/tree/downloads"
            every {
                contentResolver.takePersistableUriPermission(
                    mockUri,
                    any()
                )
            } throws SecurityException("Permission denied")

            repository.insertBackupMetadata(mockUri)

            coVerify(exactly = 1) { backupMetadataDao.insertBackupMetadata(any()) }
        }

    @Test
    fun insertBackupMetadata_whenUriIsNotContentScheme_shouldSkipPersistablePermissionAndInsertToDao() =
        runTest {
            val mockUri = mockk<Uri>()
            every { mockUri.scheme } returns "file"
            every { mockUri.path } returns "/sdcard/SafeBoxBackup"
            every { mockUri.toString() } returns "file:///sdcard/SafeBoxBackup"

            repository.insertBackupMetadata(mockUri)

            verify(exactly = 0) { contentResolver.takePersistableUriPermission(any(), any()) }
            coVerify(exactly = 1) {
                backupMetadataDao.insertBackupMetadata(
                    match { it.displayPath == "/sdcard/SafeBoxBackup" && it.uriString == "file:///sdcard/SafeBoxBackup" }
                )
            }
        }

    @Test
    fun deleteBackupMetadata_shouldDelegateToDao() = runTest {
        repository.deleteBackupMetadata()

        coVerify(exactly = 1) { backupMetadataDao.deleteBackupMetadata() }
    }

    @Test
    fun updateLastBackupDate_shouldDelegateToDao() = runTest {
        repository.updateLastBackupDate(1700000000000L)

        coVerify(exactly = 1) { backupMetadataDao.updateLastBackupDate(1700000000000L) }
    }

    @Test
    fun isBackupPathSet_whenCountGreaterThanZero_shouldReturnTrue() = runTest {
        coEvery { backupMetadataDao.isBackupPathSet() } returns 1

        assertThat(repository.isBackupPathSet()).isTrue()
    }

    @Test
    fun isBackupPathSet_whenCountIsZero_shouldReturnFalse() = runTest {
        coEvery { backupMetadataDao.isBackupPathSet() } returns 0

        assertThat(repository.isBackupPathSet()).isFalse()
    }

    @Test
    fun getBackupMetadata_whenEntityIsNull_shouldEmitNull() = runTest {
        every { backupMetadataDao.getBackupMetadata() } returns flowOf(null)

        repository.getBackupMetadata().test {
            val item = awaitItem()
            assertThat(item).isNull()
            awaitComplete()
        }
    }

    @Test
    fun getBackupMetadata_whenEntityHasNullLastBackupDate_shouldEmitBackupPathDataWithLastBackupTimeNA() =
        runTest {
            val entity = BackupMetadataEntity(
                key = 1,
                uriString = "content://path",
                displayPath = "/path",
                lastBackupDate = null,
                createdOn = Date()
            )
            every { backupMetadataDao.getBackupMetadata() } returns flowOf(entity)

            repository.getBackupMetadata().test {
                val item = awaitItem()
                assertThat(item).isNotNull()
                assertThat(item?.lastBackupTime).isEqualTo("NA")
                assertThat(item?.path).isEqualTo("/path")
                awaitComplete()
            }
        }

    @Test
    fun getBackupMetadata_whenEntityHasNonNullLastBackupDate_shouldEmitBackupPathDataWithFormattedDate() =
        runTest {
            val testDate = TestFixtures.fixedDate
            val expectedFormattedDate = Utils.getFormattedDate(testDate)
            val entity = BackupMetadataEntity(
                key = 1,
                uriString = "content://path",
                displayPath = "/path",
                lastBackupDate = testDate,
                createdOn = testDate
            )
            every { backupMetadataDao.getBackupMetadata() } returns flowOf(entity)

            repository.getBackupMetadata().test {
                val item = awaitItem()
                assertThat(item).isNotNull()
                assertThat(item?.lastBackupTime).isEqualTo(expectedFormattedDate)
                awaitComplete()
            }
        }
}
