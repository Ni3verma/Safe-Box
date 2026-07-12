@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.andryoga.safebox.worker.BackupDataWorker
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/**
 * Comprehensive Unit Test suite for [NewBackupOrRestoreVM].
 *
 * Verifies:
 * - Initialization for both Backup and Restore operations (`initVM`).
 * - Password validation and workflow state updates (`WRONG_PASSWORD` vs `IN_PROGRESS`).
 * - Static companion method delegation for backup request enqueuing (`BackupDataWorker.enqueueRequest`).
 * - WorkRequest construction, input data encryption (`SymmetricKeyUtils`), and unique enqueuing for restore operations.
 * - Reactive state machine monitoring of `WorkInfo.State` updates (`ENQUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`).
 * - In-app review trigger on restore success via `startReviewOnRestoreSuccess` channel flow.
 */
class NewBackupOrRestoreVMTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @RelaxedMockK
    lateinit var userDetailsRepository: UserDetailsRepository

    @RelaxedMockK
    lateinit var workManager: WorkManager

    @RelaxedMockK
    lateinit var symmetricKeyUtils: SymmetricKeyUtils

    @RelaxedMockK
    lateinit var analyticsHelper: AnalyticsHelper

    @RelaxedMockK
    lateinit var inAppReviewManager: InAppReviewManager

    private lateinit var lazyInAppReviewManager: Lazy<InAppReviewManager>
    private lateinit var viewModel: NewBackupOrRestoreVM

    private val testRequestId: UUID = UUID.randomUUID()
    private val workInfoFlow = MutableStateFlow<WorkInfo?>(null)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(BackupDataWorker)

        lazyInAppReviewManager = Lazy { inAppReviewManager }
        every { workManager.getWorkInfoByIdFlow(any()) } returns workInfoFlow
        every {
            BackupDataWorker.enqueueRequest(any(), any(), any(), any())
        } returns testRequestId

        viewModel = NewBackupOrRestoreVM(
            userDetailsRepository,
            workManager,
            symmetricKeyUtils,
            analyticsHelper,
            lazyInAppReviewManager
        )
    }

    @After
    fun tearDown() {
        unmockkObject(BackupDataWorker)
    }

    @Test
    fun `initVM with Backup operation initializes workflowState to ASK_FOR_PASSWORD`() = runTest {
        viewModel.initVM(Operation.Backup)

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.workflowState).isEqualTo(WorkflowState.ASK_FOR_PASSWORD)
        }
    }

    @Test
    fun `initVM with Restore operation initializes workflowState to ASK_FOR_PASSWORD`() = runTest {
        val fileUri: Uri = mockk()
        viewModel.initVM(Operation.Restore(fileUri))

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.workflowState).isEqualTo(WorkflowState.ASK_FOR_PASSWORD)
        }
    }

    @Test
    fun `PasswordConfirmed on Backup with incorrect password updates workflowState to WRONG_PASSWORD and does not enqueue work`() =
        runTest {
            viewModel.initVM(Operation.Backup)
            val password = "wrong_password"
            coEvery { userDetailsRepository.checkPassword(password) } returns false

            viewModel.onScreenAction(ScreenAction.PasswordConfirmed(password))
            advanceUntilIdle()

            viewModel.uiState.test {
                assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.WRONG_PASSWORD)
            }
            verify(exactly = 0) { BackupDataWorker.enqueueRequest(any(), any(), any(), any()) }
        }

    @Test
    fun `PasswordConfirmed on Backup with correct password enqueues BackupDataWorker`() = runTest {
        viewModel.initVM(Operation.Backup)
        val password = "correct_password"
        coEvery { userDetailsRepository.checkPassword(password) } returns true

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed(password))
        advanceUntilIdle()

        verify {
            BackupDataWorker.enqueueRequest(
                password = password,
                showBackupStartNotification = true,
                workManager = workManager,
                symmetricKeyUtils = symmetricKeyUtils
            )
        }
    }

    @Test
    fun `PasswordConfirmed on Restore skips checkPassword, logs RESTORE_STARTED, and enqueues RestoreDataWorker with encrypted password and fileUri`() =
        runTest {
            val fileUri: Uri = mockk(relaxed = true)
            viewModel.initVM(Operation.Restore(fileUri))
            val password = "restore_password"
            val encryptedPassword = "encrypted_restore_password"
            every { symmetricKeyUtils.encrypt(password) } returns encryptedPassword

            val workRequestSlot = slot<OneTimeWorkRequest>()
            every {
                workManager.enqueueUniqueWork(
                    CommonConstants.WORKER_NAME_RESTORE_DATA,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    capture(workRequestSlot)
                )
            } returns mockk()

            viewModel.onScreenAction(ScreenAction.PasswordConfirmed(password))
            advanceUntilIdle()

            coVerify(exactly = 0) { userDetailsRepository.checkPassword(any()) }
            verify { analyticsHelper.logEvent(AnalyticsKey.RESTORE_STARTED) }
            verify {
                workManager.enqueueUniqueWork(
                    CommonConstants.WORKER_NAME_RESTORE_DATA,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    any<OneTimeWorkRequest>()
                )
            }

            val inputData = workRequestSlot.captured.workSpec.input
            assertThat(inputData.getString(CommonConstants.RESTORE_PARAM_PASSWORD)).isEqualTo(
                encryptedPassword
            )
            assertThat(inputData.getString(CommonConstants.RESTORE_PARAM_FILE_URI)).isEqualTo(
                fileUri.toString()
            )
        }

    @Test
    fun `when workInfo transitions to ENQUEUED, workflowState updates to IN_PROGRESS`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.ENQUEUED }
        workInfoFlow.value = mockWorkInfo

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.IN_PROGRESS)
        }
    }

    @Test
    fun `when workInfo transitions to RUNNING, workflowState updates to IN_PROGRESS`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.RUNNING }
        workInfoFlow.value = mockWorkInfo

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.IN_PROGRESS)
        }
    }

    @Test
    fun `when workInfo transitions to SUCCEEDED on Backup, workflowState updates to SUCCESS and no review event emitted`() =
        runTest {
            viewModel.initVM(Operation.Backup)
            coEvery { userDetailsRepository.checkPassword(any()) } returns true
            val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.SUCCEEDED }
            workInfoFlow.value = mockWorkInfo

            viewModel.startReviewOnRestoreSuccess.test {
                viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
                advanceUntilIdle()

                expectNoEvents()
                assertThat(viewModel.uiState.value.workflowState).isEqualTo(WorkflowState.SUCCESS)
            }
        }

    @Test
    fun `when workInfo transitions to SUCCEEDED on Restore, workflowState updates to SUCCESS and review event emitted`() =
        runTest {
            val fileUri: Uri = mockk(relaxed = true)
            viewModel.initVM(Operation.Restore(fileUri))
            val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.SUCCEEDED }
            workInfoFlow.value = mockWorkInfo

            viewModel.startReviewOnRestoreSuccess.test {
                viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
                advanceUntilIdle()

                assertThat(awaitItem()).isEqualTo(Unit)
                assertThat(viewModel.uiState.value.workflowState).isEqualTo(WorkflowState.SUCCESS)
            }
        }

    @Test
    fun `when workInfo transitions to FAILED, workflowState updates to FAILED`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.FAILED }
        workInfoFlow.value = mockWorkInfo

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
        }
    }

    @Test
    fun `when workInfo transitions to BLOCKED, workflowState updates to FAILED`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.BLOCKED }
        workInfoFlow.value = mockWorkInfo

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
        }
    }

    @Test
    fun `when workInfo transitions to CANCELLED, workflowState updates to FAILED`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.CANCELLED }
        workInfoFlow.value = mockWorkInfo

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
        }
    }

    @Test
    fun `when workInfo is null, workflowState updates to FAILED`() = runTest {
        viewModel.initVM(Operation.Backup)
        coEvery { userDetailsRepository.checkPassword(any()) } returns true
        workInfoFlow.value = null

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
        }
    }
}
