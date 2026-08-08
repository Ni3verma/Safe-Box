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
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_IS_SHOW_START_NOTIFICATION
import com.andryoga.safebox.common.CommonConstants.BACKUP_PARAM_PASSWORD
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.core.InAppReviewManager
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    lateinit var workManager: WorkManager

    @RelaxedMockK
    lateinit var symmetricKeyUtils: SymmetricKeyUtils

    @RelaxedMockK
    lateinit var analyticsHelper: AnalyticsHelper

    @RelaxedMockK
    lateinit var inAppReviewManager: InAppReviewManager

    private lateinit var lazyInAppReviewManager: Lazy<InAppReviewManager>
    private lateinit var viewModel: NewBackupOrRestoreVM

    private val workInfoFlow = MutableStateFlow<WorkInfo?>(null)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        lazyInAppReviewManager = Lazy { inAppReviewManager }
        every { workManager.getWorkInfoByIdFlow(any()) } returns workInfoFlow

        viewModel = NewBackupOrRestoreVM(
            workManager,
            symmetricKeyUtils,
            analyticsHelper,
            lazyInAppReviewManager,
            isDebug = true
        )
    }

    @Test
    fun initialUiState_whenIsDebugTrue_prefillsDebugPassword() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.defaultPassword).isEqualTo("Qwerty@@135")
        }
    }

    @Test
    fun initialUiState_whenIsDebugFalse_defaultPasswordShouldBeEmpty() = runTest {
        val prodViewModel = NewBackupOrRestoreVM(
            workManager,
            symmetricKeyUtils,
            analyticsHelper,
            lazyInAppReviewManager,
            isDebug = false
        )

        prodViewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.defaultPassword).isEmpty()
        }
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
    fun `PasswordConfirmed on Backup with any password logs BACKUP_STARTED and enqueues BackupDataWorker`() =
        runTest {
            viewModel.initVM(Operation.Backup)
            val password = "any_password"
            val encryptedPassword = "encrypted_any_password"
        every { symmetricKeyUtils.encrypt(password) } returns encryptedPassword

        val workRequestSlot = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                CommonConstants.WORKER_NAME_BACKUP_DATA,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                capture(workRequestSlot)
            )
        } returns mockk()

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed(password))
        advanceUntilIdle()

            verify { analyticsHelper.logEvent(AnalyticsKey.BACKUP_STARTED) }
        verify {
            workManager.enqueueUniqueWork(
                CommonConstants.WORKER_NAME_BACKUP_DATA,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
        val inputData = workRequestSlot.captured.workSpec.input
        assertThat(inputData.getString(BACKUP_PARAM_PASSWORD)).isEqualTo(encryptedPassword)
        assertThat(inputData.getBoolean(BACKUP_PARAM_IS_SHOW_START_NOTIFICATION, false)).isTrue()
    }

    @Test
    fun `PasswordConfirmed on Restore logs RESTORE_STARTED and enqueues RestoreDataWorker with encrypted password and fileUri`() =
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
            val mockWorkInfo: WorkInfo = mockk { every { state } returns WorkInfo.State.SUCCEEDED }
            workInfoFlow.value = mockWorkInfo

            viewModel.startReviewOnRestoreSuccess.test {
                viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
                advanceUntilIdle()

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
    fun `when workInfo transitions to FAILED on Restore with INCORRECT_PASSWORD, workflowState updates to WRONG_PASSWORD`() =
        runTest {
            val fileUri: Uri = mockk(relaxed = true)
            viewModel.initVM(Operation.Restore(fileUri))
            val mockWorkInfo: WorkInfo = mockk {
                every { state } returns WorkInfo.State.FAILED
                every { outputData } returns RestoreFailureReason.INCORRECT_PASSWORD.toWorkData()
            }
            workInfoFlow.value = mockWorkInfo

            viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
            advanceUntilIdle()

            viewModel.uiState.test {
                assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.WRONG_PASSWORD)
            }
        }

    @Test
    fun `when workInfo transitions to FAILED on Restore with CORRUPT_OR_INVALID_FILE, workflowState updates to CORRUPT_FILE`() =
        runTest {
            val fileUri: Uri = mockk(relaxed = true)
            viewModel.initVM(Operation.Restore(fileUri))
            val mockWorkInfo: WorkInfo = mockk {
                every { state } returns WorkInfo.State.FAILED
                every { outputData } returns RestoreFailureReason.CORRUPT_OR_INVALID_FILE.toWorkData()
            }
            workInfoFlow.value = mockWorkInfo

            viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
            advanceUntilIdle()

            viewModel.uiState.test {
                assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.CORRUPT_FILE)
            }
        }

    @Test
    fun `when workInfo transitions to FAILED on Restore with UNKNOWN_ERROR, workflowState updates to FAILED`() =
        runTest {
            val fileUri: Uri = mockk(relaxed = true)
            viewModel.initVM(Operation.Restore(fileUri))
            val mockWorkInfo: WorkInfo = mockk {
                every { state } returns WorkInfo.State.FAILED
                every { outputData } returns RestoreFailureReason.UNKNOWN_ERROR.toWorkData()
            }
            workInfoFlow.value = mockWorkInfo

            viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
            advanceUntilIdle()

            viewModel.uiState.test {
                assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
            }
        }

    @Test
    fun `when workInfo transitions to FAILED on Backup, workflowState updates to FAILED`() =
        runTest {
        viewModel.initVM(Operation.Backup)
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
        workInfoFlow.value = null

        viewModel.onScreenAction(ScreenAction.PasswordConfirmed("password"))
        advanceUntilIdle()

        viewModel.uiState.test {
            assertThat(awaitItem().workflowState).isEqualTo(WorkflowState.FAILED)
        }
    }
}
