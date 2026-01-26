@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    private var job: Job? = null


    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.isPrivacyEnabled } returns isPrivacyEnabledFlow
        every { backupMetadataRepository.getBackupMetadata() } returns backupMetadataFlow
        every { activeSessionManager.logoutEvent } returns logoutEventFlow

        viewModel = MainViewModel(
            backupMetadataRepository,
            settingsDataStore,
            activeSessionManager,
            encryptedPreferenceProvider
        )
    }

    @After
    fun tearDown() {
        job?.cancel()
    }

    @Test
    fun `isBackupPathSet state is false when backup metadata is null`() = runTest {
        val items = mutableListOf<Boolean>()
        job = backgroundScope.launch {
            viewModel.isBackupPathSet.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(2)
        assertThat(items[0]).isTrue()
        assertThat(items[1]).isFalse()
    }

    @Test
    fun `isBackupPathSet state is true when backup metadata is not null`() = runTest {
        backupMetadataFlow.emit(BackupPathData("uri_path", "file_name", ""))
        val items = mutableListOf<Boolean>()
        job = backgroundScope.launch {
            viewModel.isBackupPathSet.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(1)
        assertThat(items[0]).isTrue() // stateflow do not re-emit same value
    }

    @Test
    fun `logout event is emitted from viewmodel`() = runTest {
        val items = mutableListOf<Unit>()
        job = backgroundScope.launch {
            viewModel.logoutEvent.collect { items.add(it) }
        }

        logoutEventFlow.emit(Unit)
        runCurrent()

        assertThat(items.size).isEqualTo(1)
    }

    @Test
    fun `emitted state of loading state when signup is not required`() = runTest {
        coEvery {
            encryptedPreferenceProvider.getBooleanPref(
                IS_SIGN_UP_REQUIRED,
                true
            )
        } returns false
        val items = mutableListOf<LoadingState>()
        job = backgroundScope.launch {
            viewModel.loadingState.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(2)
        assertThat(items[0])
            .isEqualTo(LoadingState.Initial)
        assertThat(items[1])
            .isEqualTo(LoadingState.ProceedToLogin)
    }

    @Test
    fun `emitted state of loading state when signup is required`() = runTest {
        coEvery {
            encryptedPreferenceProvider.getBooleanPref(
                IS_SIGN_UP_REQUIRED,
                true
            )
        } returns true
        val items = mutableListOf<LoadingState>()
        job = backgroundScope.launch {
            viewModel.loadingState.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(2)
        assertThat(items[0])
            .isEqualTo(LoadingState.Initial)
        assertThat(items[1])
            .isEqualTo(LoadingState.ProceedToSignup)
    }

    @Test
    fun `isPrivacyEnabled emits true when settings emits true`() = runTest {
        val items = mutableListOf<Boolean>()
        job = backgroundScope.launch {
            viewModel.isPrivacyEnabled.collect { items.add(it) }
        }

        runCurrent()

        assertThat(items.size).isEqualTo(1)
        assertThat(items[0]).isTrue()
    }

    @Test
    fun `isPrivacyEnabled emits false when settings emits false`() = runTest {
        val items = mutableListOf<Boolean>()
        job = backgroundScope.launch {
            viewModel.isPrivacyEnabled.collect { items.add(it) }
        }

        isPrivacyEnabledFlow.emit(false)
        runCurrent()

        assertThat(items.size).isEqualTo(1)
        assertThat(items[0]).isFalse()
    }

    @Test
    fun `isPrivacyEnabled does not emit duplicate value when settings emits prev value`() =
        runTest {
            val items = mutableListOf<Boolean>()
            job = backgroundScope.launch {
                viewModel.isPrivacyEnabled.collect { items.add(it) }
            }

            isPrivacyEnabledFlow.emit(false)
            runCurrent()
            isPrivacyEnabledFlow.emit(false) // false again
            runCurrent()

            assertThat(items.size).isEqualTo(1) //emits once
            assertThat(items[0]).isFalse()
        }

    @Test
    fun `isPrivacyEnabled emit updated value when settings emits new value`() = runTest {
        val items = mutableListOf<Boolean>()
        job = backgroundScope.launch {
            viewModel.isPrivacyEnabled.collect { items.add(it) }
        }

        isPrivacyEnabledFlow.emit(false)
        runCurrent()
        isPrivacyEnabledFlow.emit(true) // changed
        runCurrent()

        assertThat(items.size).isEqualTo(2)
        assertThat(items).isEqualTo(listOf(false, true))
    }

    @Test
    fun `updateTopBar updates the state to visible`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {})
        viewModel.updateTopBar(topAppBarConfig)

        assertThat(viewModel.topBarState.value).isEqualTo(TopBarState.Visible(topAppBarConfig))
    }

    @Test
    fun `updateTopBar updates the state second time`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.NONE)
        val topAppBarConfigNew = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.ENTER_ALWAYS)

        viewModel.updateTopBar(topAppBarConfig)
        viewModel.updateTopBar(topAppBarConfigNew)

        assertThat(viewModel.topBarState.value).isEqualTo(TopBarState.Visible(topAppBarConfigNew))
    }

    @Test
    fun `hideTopBar updates the state to hidden`() = runTest {
        val topAppBarConfig = TopAppBarConfig({}, {}, {}, ScrollBehaviorType.NONE)

        viewModel.updateTopBar(topAppBarConfig)
        viewModel.hideTopBar()

        assertThat(viewModel.topBarState.value).isEqualTo(TopBarState.Hidden)
    }
}
