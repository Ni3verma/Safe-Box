@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.settings

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.DispatchersProvider
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @RelaxedMockK
    lateinit var settingsDataStore: SettingsDataStore

    @RelaxedMockK
    lateinit var analyticsHelper: AnalyticsHelper

    private lateinit var viewModel: SettingsViewModel

    private val settingsFlow = MutableStateFlow(Settings())

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        val testDispatcher = UnconfinedTestDispatcher()
        val dispatchersProvider = object : DispatchersProvider {
            override val main: CoroutineDispatcher
                get() = testDispatcher
            override val default: CoroutineDispatcher
                get() = testDispatcher
            override val io: CoroutineDispatcher
                get() = testDispatcher
        }
        every { settingsDataStore.settingsFlow } returns settingsFlow
        viewModel = SettingsViewModel(settingsDataStore, analyticsHelper, dispatchersProvider)
    }

    @Test
    fun `uiState reflects settings from data store`() = runTest {
        val settings = Settings(isPrivacyEnabled = true, awayTimeoutSec = 10)
        settingsFlow.value = settings

        val uiState = viewModel.uiState.first()

        assertThat(uiState).isEqualTo(settings)
    }

    @Test
    fun `onScreenAction UpdatePrivacy calls data store`() = runTest {
        val enabled = true
        val action = SettingsScreenAction.UpdatePrivacy(enabled)

        viewModel.onScreenAction(action)

        coVerify { settingsDataStore.updatePrivacy(enabled) }
    }

    @Test
    fun `onScreenAction UpdateAutoBackupAfterLogin calls data store`() = runTest {
        val enabled = true
        val action = SettingsScreenAction.UpdateAutoBackupAfterLogin(enabled)

        viewModel.onScreenAction(action)

        coVerify { settingsDataStore.updateAutoBackupAfterPasswordLogin(enabled) }
    }

    @Test
    fun `onScreenAction UpdateAwayTimeout calls data store`() = runTest {
        val timeout = 15
        val action = SettingsScreenAction.UpdateAwayTimeout(timeout)

        viewModel.onScreenAction(action)

        coVerify { settingsDataStore.updateAwayTimeout(timeout) }
    }

    @Test
    fun `onScreenAction UpdatePasswordAfterXBiometric calls data store`() = runTest {
        val limit = 5
        val action = SettingsScreenAction.UpdatePasswordAfterXBiometric(limit)

        viewModel.onScreenAction(action)

        coVerify { settingsDataStore.updatePasswordAfterXBiometricLogin(limit) }
    }

    @Test
    fun `onScreenAction OpenGithubProject logs event`() {
        val action = SettingsScreenAction.OpenGithubProject

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.OPEN_GITHUB) }

    }

    @Test
    fun `onScreenAction ReviewApp logs event`() {
        val action = SettingsScreenAction.ReviewApp

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.OPEN_PLAY_STORE) }
    }

    @Test
    fun `onScreenAction SendFeedback logs event`() {
        val action = SettingsScreenAction.SendFeedback

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.EMAIL_FEEDBACK) }
    }
}