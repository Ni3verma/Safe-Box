@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri
import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
        backupMetadataFlow.value = null
        every { backupMetadataRepository.getBackupMetadata() } returns backupMetadataFlow
    }

    private fun initViewModel(startWithRestore: Boolean = false) {
        val route = BackupAndRestoreRoute(startWithRestore)
        every { backupAndRestoreRouteProvider.getRoute() } returns route
        viewModel = BackupAndRestoreVM(
            backupMetadataRepository,
            activeSessionManager,
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
        viewModel.startRestoreWorkflow.test {}
    }

    @Test
    fun `init sets BackupPathNotSet state when backup metadata is null`() = runTest {
        backupMetadataFlow.value = null
        initViewModel()

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.backupState).isInstanceOf(Loading::class.java)

            val updatedState = awaitItem()
            assertThat(updatedState.backupState).isInstanceOf(BackupPathNotSet::class.java)
        }
    }

    @Test
    fun `init sets BackupPathSet state when backup metadata is available`() = runTest {
        val metadata = BackupPathData("uri", "test_path", "time")
        backupMetadataFlow.value = metadata
        initViewModel()

        viewModel.uiState.test {
            val initialState = awaitItem()
            assertThat(initialState.backupState).isInstanceOf(Loading::class.java)

            val updatedState = awaitItem()
            assertThat(updatedState.backupState).isInstanceOf(BackupPathSet::class.java)
            val backupState = updatedState.backupState as BackupPathSet
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
        advanceUntilIdle()

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

