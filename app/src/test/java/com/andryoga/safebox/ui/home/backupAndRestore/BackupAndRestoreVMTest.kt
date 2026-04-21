@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.home.navigation.HomeRouteType.BackupAndRestoreRoute
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class BackupAndRestoreVMTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @RelaxedMockK
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @RelaxedMockK
    lateinit var activeSessionManager: ActiveSessionManager

    @RelaxedMockK
    lateinit var backupAndRestoreRouteProvider: BackupAndRestoreRouteProvider

    private lateinit var viewModel: BackupAndRestoreVM

    private val backupMetadataFlow = MutableStateFlow<BackupPathData?>(null)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backupMetadataRepository.getBackupMetadata() } returns backupMetadataFlow
    }

    private fun initViewModel(startWithRestore: Boolean = false) {
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher
                get() = testDispatcher
            override val default: CoroutineDispatcher
                get() = testDispatcher
            override val io: CoroutineDispatcher
                get() = testDispatcher
        }
        val route = BackupAndRestoreRoute(startWithRestore)
        every { backupAndRestoreRouteProvider.getRoute() } returns route
        viewModel = BackupAndRestoreVM(
            backupMetadataRepository,
            activeSessionManager,
            dispatchersProvider,
            backupAndRestoreRouteProvider
        )
    }

    @Test
    fun `init starts with restore workflow when startWithRestoreWorkflow is true`() = runTest {
        initViewModel(startWithRestore = true)
        viewModel.startRestoreWorkflow.test {
            assertThat(awaitItem()).isEqualTo(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init does not start restore workflow when startWithRestoreWorkflow is false`() = runTest {
        initViewModel(startWithRestore = false)
        viewModel.startRestoreWorkflow.test(timeout = 2.seconds) {
            expectNoEvents()
        }
    }

    @Test
    fun `init sets BackupPathNotSet state when backup metadata is null`() = runTest {
        initViewModel()
        backupMetadataFlow.value = null
        viewModel.uiState.test {
            // Skip initial Loading state
            skipItems(1)
            val item = awaitItem()
            assertThat(item.backupState).isInstanceOf(BackupPathNotSet::class.java)
        }
    }

    @Test
    fun `init sets BackupPathSet state when backup metadata is available`() = runTest {
        initViewModel()
        val metadata = BackupPathData("uri", "test_path", "time")
        backupMetadataFlow.value = metadata

        viewModel.uiState.test {
            // Skip initial state
            skipItems(1)
            val uiState = awaitItem()
            assertThat(uiState.backupState).isInstanceOf(BackupPathSet::class.java)
            val backupState = uiState.backupState as BackupPathSet
            assertThat(backupState.backupPath).isEqualTo(metadata.path)
            assertThat(backupState.backupTime).isEqualTo(metadata.lastBackupTime)
        }
    }

    @Test
    fun `onScreenAction BackupPathSelected inserts metadata`() = runTest {
        initViewModel()
        val uri: Uri = mockk()
        val action = ScreenAction.BackupPathSelected(uri)

        viewModel.onScreenAction(action)

        coVerify { backupMetadataRepository.insertBackupMetadata(uri) }
    }

    @Test
    fun `onScreenAction NewBackupClick updates state to StartedForBackup`() = runTest {
        initViewModel()
        val action = ScreenAction.NewBackupClick

        viewModel.onScreenAction(action)

        viewModel.uiState.test {
            val uiState = awaitItem()
            assertThat(uiState.newBackupOrRestoreScreenState)
                .isInstanceOf(NewBackupOrRestoreScreenState.StartedForBackup::class.java)
        }
    }

    @Test
    fun `onScreenAction NewBackupOrRestoreDismiss updates state to NotStarted`() = runTest {
        initViewModel()
        viewModel.onScreenAction(ScreenAction.NewBackupClick) // Start it first
        val action = ScreenAction.NewBackupOrRestoreDismiss

        viewModel.onScreenAction(action)

        viewModel.uiState.test {
            val uiState = awaitItem()
            assertThat(uiState.newBackupOrRestoreScreenState)
                .isInstanceOf(NewBackupOrRestoreScreenState.NotStarted::class.java)
        }
    }

    @Test
    fun `onScreenAction RestoreFileSelected updates state to StartedForRestore with valid URI`() =
        runTest {
            initViewModel()
            val uri: Uri = mockk()
            val action = ScreenAction.RestoreFileSelected(uri)

            viewModel.onScreenAction(action)

            viewModel.uiState.test {
                val uiState = awaitItem()
                assertThat(uiState.newBackupOrRestoreScreenState)
                    .isInstanceOf(NewBackupOrRestoreScreenState.StartedForRestore::class.java)
                val restoreState =
                    uiState.newBackupOrRestoreScreenState as NewBackupOrRestoreScreenState.StartedForRestore
                assertThat(restoreState.fileUri).isEqualTo(uri)
            }
        }

    @Test
    fun `onScreenAction RestoreFileSelected does not update state with null URI`() = runTest {
        initViewModel()
        val initialState = viewModel.uiState.value

        val action = ScreenAction.RestoreFileSelected(null)
        viewModel.onScreenAction(action)

        val finalState = viewModel.uiState.value
        assertThat(finalState.newBackupOrRestoreScreenState)
            .isEqualTo(initialState.newBackupOrRestoreScreenState)
    }

    @Test
    fun `pauseActiveSessionManager pauses the session manager`() {
        initViewModel()
        viewModel.pauseActiveSessionManager()
        verify { activeSessionManager.setPaused(true) }
    }

    @Test
    fun `resumeActiveSessionManager resumes the session manager`() {
        initViewModel()
        viewModel.resumeActiveSessionManager()
        verify { activeSessionManager.setPaused(false) }
    }
}
