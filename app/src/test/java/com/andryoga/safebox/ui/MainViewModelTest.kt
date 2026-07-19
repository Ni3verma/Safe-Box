@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.ScrollBehaviorType
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.core.TopBarState
import com.andryoga.safebox.ui.loading.LoadingState
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    @MockK
    lateinit var activeSessionManager: ActiveSessionManager

    @MockK
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    private lateinit var viewModel: MainViewModel

    private val isPrivacyEnabledFlow = MutableStateFlow(true)
    private val backupMetadataFlow = MutableStateFlow<BackupPathData?>(null)
    private val logoutEventFlow = MutableSharedFlow<Unit>(replay = 1)


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.isPrivacyEnabledFlow } returns isPrivacyEnabledFlow
        every { backupMetadataRepository.getBackupMetadata() } returns backupMetadataFlow
        every { activeSessionManager.logoutEvent } returns logoutEventFlow

        viewModel = MainViewModel(
            backupMetadataRepository,
            settingsDataStore,
            activeSessionManager,
            encryptedPreferenceProvider
        )
    }

    @Test
    fun `isBackupPathSet state is false when backup metadata is null`() = runTest {
        viewModel.isBackupPathSet.test {
            assertThat(awaitItem()).isTrue()
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `isBackupPathSet state is true when backup metadata is not null`() = runTest {
        backupMetadataFlow.emit(BackupPathData("uri_path", "file_name", ""))
        viewModel.isBackupPathSet.test {
            assertThat(awaitItem()).isTrue() // stateflow do not re-emit same value
        }
    }

    @Test
    fun `logout event is emitted from viewmodel`() = runTest {
        viewModel.logoutEvent.test {
            logoutEventFlow.emit(Unit)
            assertThat(awaitItem()).isEqualTo(Unit)
        }
    }

    @Test
    fun `emitted state of loading state when signup is not required`() = runTest {
        coEvery {
            encryptedPreferenceProvider.getBooleanPref(
                IS_SIGN_UP_REQUIRED,
                true
            )
        } returns false

        viewModel.loadingState.test {
            assertThat(awaitItem()).isEqualTo(LoadingState.Initial)
            assertThat(awaitItem()).isEqualTo(LoadingState.ProceedToLogin)
        }
    }

    @Test
    fun `emitted state of loading state when signup is required`() = runTest {
        coEvery {
            encryptedPreferenceProvider.getBooleanPref(
                IS_SIGN_UP_REQUIRED,
                true
            )
        } returns true

        viewModel.loadingState.test {
            assertThat(awaitItem()).isEqualTo(LoadingState.Initial)
            assertThat(awaitItem()).isEqualTo(LoadingState.ProceedToSignup)
        }
    }

    @Test
    fun `isPrivacyEnabled emits true when settings emits true`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `isPrivacyEnabled emits false when settings emits false`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
            isPrivacyEnabledFlow.emit(false)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `isPrivacyEnabled does not emit duplicate value when settings emits prev value`() =
        runTest {
            viewModel.isPrivacyEnabled.test {
                assertThat(awaitItem()).isTrue()
                isPrivacyEnabledFlow.emit(false)
                assertThat(awaitItem()).isFalse()

                isPrivacyEnabledFlow.emit(false) // false again
                advanceUntilIdle()
                expectNoEvents() // no duplicate emission
            }
        }

    @Test
    fun `isPrivacyEnabled emit updated value when settings emits new value`() = runTest {
        viewModel.isPrivacyEnabled.test {
            assertThat(awaitItem()).isTrue()
            isPrivacyEnabledFlow.emit(false)
            assertThat(awaitItem()).isFalse()
            isPrivacyEnabledFlow.emit(true) // changed
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `updateTopBar updates the state to visible`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {})
        viewModel.topBarState.test {
            assertThat(awaitItem()).isEqualTo(TopBarState.Hidden)
            viewModel.updateTopBar(topAppBarConfig)
            assertThat(awaitItem()).isEqualTo(TopBarState.Visible(topAppBarConfig))
        }
    }

    @Test
    fun `updateTopBar updates the state second time`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.NONE)
        val topAppBarConfigNew = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.ENTER_ALWAYS)

        viewModel.topBarState.test {
            assertThat(awaitItem()).isEqualTo(TopBarState.Hidden)
            viewModel.updateTopBar(topAppBarConfig)
            assertThat(awaitItem()).isEqualTo(TopBarState.Visible(topAppBarConfig))
            viewModel.updateTopBar(topAppBarConfigNew)
            assertThat(awaitItem()).isEqualTo(TopBarState.Visible(topAppBarConfigNew))
        }
    }

    @Test
    fun `hideTopBar updates the state to hidden`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.NONE)

        viewModel.topBarState.test {
            assertThat(awaitItem()).isEqualTo(TopBarState.Hidden)
            viewModel.updateTopBar(topAppBarConfig)
            assertThat(awaitItem()).isEqualTo(TopBarState.Visible(topAppBarConfig))
            viewModel.hideTopBar()
            assertThat(awaitItem()).isEqualTo(TopBarState.Hidden)
        }
    }
}
