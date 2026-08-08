@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.settings

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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

    @RelaxedMockK
    lateinit var userDetailsRepository: UserDetailsRepository

    @RelaxedMockK
    lateinit var activeSessionManager: ActiveSessionManager

    private lateinit var viewModel: SettingsViewModel

    private val settingsFlow = MutableStateFlow(Settings())

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { settingsDataStore.settingsFlow } returns settingsFlow
        viewModel = SettingsViewModel(
            settingsDataStore,
            analyticsHelper,
            userDetailsRepository,
            activeSessionManager,
        )
    }

    @Test
    fun `uiState reflects settings from data store`() = runTest {
        viewModel.uiState.test {
            assertThat(awaitItem()).isEqualTo(Settings())

            val updatedSettings = Settings(isPrivacyEnabled = false, awayTimeoutSec = 30)
            settingsFlow.value = updatedSettings

            assertThat(awaitItem()).isEqualTo(updatedSettings)
        }
    }

    @Test
    fun `onScreenAction UpdatePrivacy calls data store`() = runTest {
        val enabled = true
        val action = SettingsScreenAction.UpdatePrivacy(enabled)

        viewModel.onScreenAction(action)
        advanceUntilIdle()

        coVerify { settingsDataStore.updatePrivacy(enabled) }
    }

    @Test
    fun `onScreenAction UpdateAutoBackupAfterLogin calls data store`() = runTest {
        val enabled = true
        val action = SettingsScreenAction.UpdateAutoBackupAfterLogin(enabled)

        viewModel.onScreenAction(action)
        advanceUntilIdle()

        coVerify { settingsDataStore.updateAutoBackupAfterPasswordLogin(enabled) }
    }

    @Test
    fun `onScreenAction UpdateAwayTimeout calls data store`() = runTest {
        val timeout = 15
        val action = SettingsScreenAction.UpdateAwayTimeout(timeout)

        viewModel.onScreenAction(action)
        advanceUntilIdle()

        coVerify { settingsDataStore.updateAwayTimeout(timeout) }
    }

    @Test
    fun `onScreenAction UpdatePasswordAfterXBiometric calls data store`() = runTest {
        val limit = 5
        val action = SettingsScreenAction.UpdatePasswordAfterXBiometric(limit)

        viewModel.onScreenAction(action)
        advanceUntilIdle()

        coVerify { settingsDataStore.updatePasswordAfterXBiometricLogin(limit) }
    }

    @Test
    fun `onScreenAction OpenGithubProject logs event`() = runTest {
        val action = SettingsScreenAction.OpenGithubProject

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.OPEN_GITHUB) }
    }

    @Test
    fun `onScreenAction ReviewApp logs event`() = runTest {
        val action = SettingsScreenAction.ReviewApp

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.OPEN_PLAY_STORE) }
    }

    @Test
    fun `onScreenAction SendFeedback logs event`() = runTest {
        val action = SettingsScreenAction.SendFeedback

        viewModel.onScreenAction(action)

        verify { analyticsHelper.logEvent(AnalyticsKey.EMAIL_FEEDBACK) }
    }

    @Test
    fun `onScreenAction OnUpdateMasterPassword updates master password and hint via repository`() =
        runTest {
            viewModel.onScreenAction(
                SettingsScreenAction.OnUpdateMasterPassword(
                    newPassword = "UpdatedPass@@123",
                    hint = "updated hint"
                )
            )
            advanceUntilIdle()

            coVerify {
                userDetailsRepository.updatePasswordAndHint("UpdatedPass@@123", "updated hint")
            }
            verify { analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK) }
        }

    @Test
    fun `OnDeviceSecurityRequiredDialogShown logs DEVICE_SECURITY_REQUIRED_DIALOG_SHOW event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnDeviceSecurityRequiredDialogShown)
            verify { analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW) }
        }

    @Test
    fun `OnDeviceSecurityRequiredOpenSettingsClicked pauses activeSessionManager and logs DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked)
            verify { activeSessionManager.setPaused(true) }
            verify {
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }
        }

    @Test
    fun `OnDeviceSecurityRequiredOpenSettingsFailed logs DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed)
            verify {
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
            }
        }

    @Test
    fun `OnDeviceSecurityRequiredDismissClicked logs DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnDeviceSecurityRequiredDismissClicked)
            verify {
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }
        }

    @Test
    fun `OnUpdatePasswordDialogShown logs UPDATE_PASSWORD_DIALOG_SHOW event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnUpdatePasswordDialogShown)
            verify { analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW) }
        }

    @Test
    fun `OnUpdatePasswordDismissClicked logs UPDATE_PASSWORD_DIALOG_CANCEL_CLICK event`() =
        runTest {
            viewModel.onScreenAction(SettingsScreenAction.OnUpdatePasswordDismissClicked)
            verify { analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK) }
        }
}