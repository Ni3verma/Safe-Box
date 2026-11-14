@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.home.settings

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.ChangeMasterPasswordUseCase
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.andryoga.safebox.security.envelope.interfaces.SessionLockedException
import com.andryoga.safebox.security.envelope.models.KdfParams
import com.andryoga.safebox.security.envelope.models.VaultState
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecuritySnapshot
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @RelaxedMockK
    lateinit var settingsDataStore: SettingsDataStore

    @MockK
    lateinit var vaultRepository: VaultRepository

    @MockK
    lateinit var deviceSecurityStatus: DeviceSecurityStatus

    @RelaxedMockK
    lateinit var vaultKeyMaintenance: VaultKeyMaintenance

    @RelaxedMockK
    lateinit var changeMasterPasswordUseCase: ChangeMasterPasswordUseCase

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var viewModel: SettingsViewModel

    private val settingsFlow = MutableStateFlow(Settings())

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        sessionKeyHolder = SessionKeyHolderImpl()
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x21 })
        every { settingsDataStore.settingsFlow } returns settingsFlow
        givenEnvelopeVault(isBiometricUnlockDisabled = false)
        givenBiometricClass(BiometricClass.STRONG)
        viewModel = SettingsViewModel(
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
            sessionKeyHolder = sessionKeyHolder,
            vaultRepository = vaultRepository,
            deviceSecurityStatus = deviceSecurityStatus,
            vaultKeyMaintenance = vaultKeyMaintenance,
            changeMasterPasswordUseCase = changeMasterPasswordUseCase,
        )
    }

    private fun givenEnvelopeVault(isBiometricUnlockDisabled: Boolean) {
        coEvery { vaultRepository.getVaultState() } returns VaultState.Envelope(
            kdfParams = KdfParams(
                algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
                iterations = 99_000,
                salt = ByteArray(16) { 7 },
            ),
            wrappedDekPassword = "password-wrap",
            dekVerifier = "verifier",
            wrappedDekBiometric = if (isBiometricUnlockDisabled) null else "biometric-wrap",
            biometricKeyAliasSuffix = null,
            wrappedDekDevice = null,
            deviceKeyAliasSuffix = null,
            isBiometricUnlockDisabled = isBiometricUnlockDisabled,
        )
    }

    private fun givenBiometricClass(biometricClass: BiometricClass) {
        every { deviceSecurityStatus.snapshot() } returns DeviceSecuritySnapshot(
            isDeviceSecure = true,
            biometricClass = biometricClass,
            hasStrongBox = false,
        )
    }

    /** `uiState` is `WhileSubscribed`; a collector keeps it live for `.value` reads. */
    private fun TestScope.collectUiState() {
        backgroundScope.launch { viewModel.uiState.collect { } }
    }

    private fun TestScope.act(action: SettingsScreenAction) {
        viewModel.onScreenAction(action)
        advanceUntilIdle()
    }

    private fun assertLoggedFromSettingsScreen(key: AnalyticsKey) {
        val events = analyticsHelper.loggedEvents.filter { it.key == key }
        assertThat(events).hasSize(1)
        assertThat(events.single().params[AnalyticsParam.SOURCE.paramName])
            .isEqualTo(AnalyticsSource.SETTINGS.value)
    }

    private fun gateParam(): Any? = analyticsHelper.loggedEvents
        .single { it.key == AnalyticsKey.CHANGE_PASSWORD_GATE_PASSED }
        .params[AnalyticsParam.GATE.paramName]

    // region preferences and support rows

    @Test
    fun uiState_reflectsSettingsFromDataStore() = runTest {
        collectUiState()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.settings).isEqualTo(Settings())

        val updatedSettings = Settings(isPrivacyEnabled = false, awayTimeoutSec = 30)
        settingsFlow.value = updatedSettings
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.settings).isEqualTo(updatedSettings)
    }

    @Test
    fun updatePrivacy_shouldWriteDataStore() = runTest {
        act(SettingsScreenAction.UpdatePrivacy(true))

        coVerify { settingsDataStore.updatePrivacy(true) }
    }

    @Test
    fun updateAutoBackupAfterLogin_shouldWriteDataStore() = runTest {
        act(SettingsScreenAction.UpdateAutoBackupAfterLogin(true))

        coVerify { settingsDataStore.updateAutoBackupAfterPasswordLogin(true) }
    }

    @Test
    fun updateAwayTimeout_shouldWriteDataStore() = runTest {
        act(SettingsScreenAction.UpdateAwayTimeout(15))

        coVerify { settingsDataStore.updateAwayTimeout(15) }
    }

    @Test
    fun updatePasswordAfterXBiometric_shouldWriteDataStore() = runTest {
        act(SettingsScreenAction.UpdatePasswordAfterXBiometric(5))

        coVerify { settingsDataStore.updatePasswordAfterXBiometricLogin(5) }
    }

    @Test
    fun supportRows_shouldLogTheirEvents() = runTest {
        act(SettingsScreenAction.OpenGithubProject)
        act(SettingsScreenAction.OpenBuyMeACoffee)
        act(SettingsScreenAction.ReviewApp)
        act(SettingsScreenAction.SendFeedback)

        assertThat(analyticsHelper.loggedEvents.map { it.key }).containsExactly(
            AnalyticsKey.OPEN_GITHUB,
            AnalyticsKey.OPEN_BUY_ME_A_COFFEE,
            AnalyticsKey.OPEN_PLAY_STORE,
            AnalyticsKey.EMAIL_FEEDBACK,
        ).inOrder()
    }

    // endregion

    // region change-password gate

    @Test
    fun changePasswordClicked_whenDeviceAuthIsAvailable_shouldShowTheDevicePrompt() = runTest {
        collectUiState()

        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))

        assertThat(viewModel.uiState.value.changePasswordStep).isEqualTo(ChangePasswordStep.DeviceAuthentication)
    }

    @Test
    fun changePasswordClicked_withoutAScreenLock_shouldAskForTheCurrentPassword() = runTest {
        collectUiState()

        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        assertThat(viewModel.uiState.value.changePasswordStep).isEqualTo(ChangePasswordStep.CurrentPassword())
    }

    @Test
    fun changePasswordClicked_inALegacySession_shouldBeIgnoredAndTheStateSaysLegacy() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        collectUiState()

        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))

        assertThat(viewModel.uiState.value.isLegacySession).isTrue()
        assertThat(viewModel.uiState.value.changePasswordStep).isNull()
    }

    @Test
    fun deviceAuthSucceeded_shouldLogTheDeviceGateAndOpenTheNewPasswordDialog() = runTest {
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))

        act(SettingsScreenAction.OnDeviceAuthSucceeded)

        assertThat(gateParam()).isEqualTo(ChangePasswordGate.DEVICE_AUTH.wireName)
        assertThat(viewModel.uiState.value.changePasswordStep).isEqualTo(ChangePasswordStep.NewPassword)
    }

    @Test
    fun deviceAuthCancelledOrFailed_shouldFallBackToTheCurrentPasswordField() = runTest {
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))

        act(SettingsScreenAction.OnDeviceAuthFailedOrCancelled)

        assertThat(viewModel.uiState.value.changePasswordStep).isEqualTo(ChangePasswordStep.CurrentPassword())
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CHANGE_PASSWORD_GATE_PASSED)).isFalse()
    }

    @Test
    fun currentPasswordSubmitted_whenCorrect_shouldLogTheMasterPasswordGateAndOpenTheNewPasswordDialog() =
        runTest {
            coEvery { changeMasterPasswordUseCase.verifyCurrentPassword(any()) } returns true
            collectUiState()
            act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

            act(SettingsScreenAction.OnCurrentPasswordSubmitted("Current@@123"))

            coVerify { changeMasterPasswordUseCase.verifyCurrentPassword("Current@@123".toCharArray()) }
            assertThat(gateParam()).isEqualTo(ChangePasswordGate.MASTER_PASSWORD.wireName)
            assertThat(viewModel.uiState.value.changePasswordStep).isEqualTo(ChangePasswordStep.NewPassword)
        }

    @Test
    fun currentPasswordSubmitted_whenWrong_shouldStayOnTheFieldMarkedIncorrect() = runTest {
        coEvery { changeMasterPasswordUseCase.verifyCurrentPassword(any()) } returns false
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        act(SettingsScreenAction.OnCurrentPasswordSubmitted("Wrong@@123"))

        assertThat(viewModel.uiState.value.changePasswordStep)
            .isEqualTo(ChangePasswordStep.CurrentPassword(isIncorrect = true))
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CHANGE_PASSWORD_GATE_PASSED)).isFalse()
    }

    @Test
    fun currentPasswordSubmitted_whileACheckIsInFlight_shouldBeIgnored() = runTest {
        coEvery { changeMasterPasswordUseCase.verifyCurrentPassword(any()) } returns false
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        viewModel.onScreenAction(SettingsScreenAction.OnCurrentPasswordSubmitted("Wrong@@123"))
        viewModel.onScreenAction(SettingsScreenAction.OnCurrentPasswordSubmitted("Wrong@@123"))
        advanceUntilIdle()

        coVerify(exactly = 1) { changeMasterPasswordUseCase.verifyCurrentPassword(any()) }
    }

    @Test
    fun currentPasswordDismissed_shouldLogTheCancelAndCloseTheFlow() = runTest {
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        act(SettingsScreenAction.OnCurrentPasswordDismissed)

        assertThat(analyticsHelper.count(AnalyticsKey.CURRENT_PASSWORD_DIALOG_CANCEL_CLICK)).isEqualTo(1)
        assertThat(viewModel.uiState.value.changePasswordStep).isNull()
    }

    @Test
    fun currentPasswordDialog_shownAndSubmitted_shouldLogShowAndConfirmOncePerAcceptedSubmit() = runTest {
        coEvery { changeMasterPasswordUseCase.verifyCurrentPassword(any()) } returns false
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        act(SettingsScreenAction.OnCurrentPasswordDialogShown)
        viewModel.onScreenAction(SettingsScreenAction.OnCurrentPasswordSubmitted("Wrong@@123"))
        viewModel.onScreenAction(SettingsScreenAction.OnCurrentPasswordSubmitted("Wrong@@123"))
        advanceUntilIdle()

        assertThat(analyticsHelper.count(AnalyticsKey.CURRENT_PASSWORD_DIALOG_SHOW)).isEqualTo(1)
        // The second tap lands while the first check is in flight and is dropped.
        assertThat(analyticsHelper.count(AnalyticsKey.CURRENT_PASSWORD_DIALOG_CONFIRM_CLICK)).isEqualTo(1)
    }

    @Test
    fun currentPasswordCheck_finishingAfterTheDialogWasCancelled_shouldNotReopenTheFlow() = runTest {
        coEvery { changeMasterPasswordUseCase.verifyCurrentPassword(any()) } coAnswers {
            // The user taps Cancel while PBKDF2 runs.
            viewModel.onScreenAction(SettingsScreenAction.OnCurrentPasswordDismissed)
            true
        }
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = false))

        act(SettingsScreenAction.OnCurrentPasswordSubmitted("Current@@123"))

        assertThat(viewModel.uiState.value.changePasswordStep).isNull()
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.CHANGE_PASSWORD_GATE_PASSED)).isFalse()
    }

    /** Puts the flow on the new-password dialog the way the device prompt does. */
    private fun TestScope.passTheGate() {
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))
        act(SettingsScreenAction.OnDeviceAuthSucceeded)
    }

    @Test
    fun updateMasterPassword_shouldRewrapLogTheAllowClickAndReportSuccess() = runTest {
        collectUiState()
        passTheGate()

        act(SettingsScreenAction.OnUpdateMasterPassword(newPassword = "UpdatedPass@@123", hint = "updated hint"))

        coVerify {
            changeMasterPasswordUseCase.changePassword("UpdatedPass@@123".toCharArray(), "updated hint")
        }
        assertLoggedFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        assertThat(viewModel.uiState.value.changePasswordStep).isNull()
        assertThat(viewModel.uiState.value.passwordChangeResult).isEqualTo(PasswordChangeResult.SUCCESS)

        act(SettingsScreenAction.OnPasswordChangeResultShown)

        assertThat(viewModel.uiState.value.passwordChangeResult).isNull()
    }

    @Test
    fun updateMasterPassword_withoutPassingTheGate_shouldWriteNothing() = runTest {
        collectUiState()

        act(SettingsScreenAction.OnUpdateMasterPassword(newPassword = "UpdatedPass@@123", hint = "updated hint"))

        coVerify(exactly = 0) { changeMasterPasswordUseCase.changePassword(any(), any()) }
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)).isFalse()
    }

    @Test
    fun updateMasterPassword_whenSaveIsTappedTwice_shouldRewrapOnce() = runTest {
        collectUiState()
        passTheGate()

        viewModel.onScreenAction(SettingsScreenAction.OnUpdateMasterPassword("UpdatedPass@@123", "updated hint"))
        viewModel.onScreenAction(SettingsScreenAction.OnUpdateMasterPassword("UpdatedPass@@123", "updated hint"))
        advanceUntilIdle()

        coVerify(exactly = 1) { changeMasterPasswordUseCase.changePassword(any(), any()) }
    }

    @Test
    fun updateMasterPassword_whenTheRewrapFails_shouldReportFailure() = runTest {
        coEvery { changeMasterPasswordUseCase.changePassword(any(), any()) } throws IOException("disk full")
        collectUiState()
        passTheGate()

        act(SettingsScreenAction.OnUpdateMasterPassword(newPassword = "UpdatedPass@@123", hint = "updated hint"))

        assertThat(viewModel.uiState.value.passwordChangeResult).isEqualTo(PasswordChangeResult.FAILURE)
    }

    @Test
    fun updateMasterPassword_whenTheVaultLocksMidCall_shouldEndSilentlyWithoutAResult() = runTest {
        coEvery { changeMasterPasswordUseCase.changePassword(any(), any()) } answers {
            sessionKeyHolder.clearSessionKey()
            throw SessionLockedException()
        }
        collectUiState()
        passTheGate()

        act(SettingsScreenAction.OnUpdateMasterPassword(newPassword = "UpdatedPass@@123", hint = "updated hint"))

        // No crash: launchVaultOperation contains the lock, and the HomeGraph gate leaves.
        assertThat(viewModel.uiState.value.passwordChangeResult).isNull()
    }

    @Test
    fun updatePasswordDialogShownAndDismissed_shouldLogTheirEventsFromSettingsAndCloseTheFlow() = runTest {
        collectUiState()
        act(SettingsScreenAction.ChangePasswordClicked(canUseDeviceAuth = true))
        act(SettingsScreenAction.OnDeviceAuthSucceeded)

        act(SettingsScreenAction.OnUpdatePasswordDialogShown)
        act(SettingsScreenAction.OnUpdatePasswordDismissClicked)

        assertLoggedFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
        assertLoggedFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
        assertThat(viewModel.uiState.value.changePasswordStep).isNull()
    }

    // endregion

    // region fingerprint-unlock switch

    @Test
    fun screenResumed_withAStrongBiometricAndNoOptOut_shouldShowTheSwitchOn() = runTest {
        collectUiState()

        act(SettingsScreenAction.OnScreenResumed)

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Available(isOn = true))
    }

    @Test
    fun screenResumed_afterTheUserOptedOut_shouldShowTheSwitchOff() = runTest {
        givenEnvelopeVault(isBiometricUnlockDisabled = true)
        collectUiState()

        act(SettingsScreenAction.OnScreenResumed)

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Available(isOn = false))
    }

    @Test
    fun screenResumed_withoutBiometricHardware_shouldHideTheSwitch() = runTest {
        givenBiometricClass(BiometricClass.NO_HARDWARE)
        collectUiState()

        act(SettingsScreenAction.OnScreenResumed)

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Hidden)
    }

    @Test
    fun screenResumed_withNoStrongBiometricEnrolled_shouldShowTheSwitchUnavailable() = runTest {
        givenBiometricClass(BiometricClass.NONE_ENROLLED)
        collectUiState()

        act(SettingsScreenAction.OnScreenResumed)

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Unavailable)
    }

    @Test
    fun screenResumed_inALegacySession_shouldHideTheSwitch() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        coEvery { vaultRepository.getVaultState() } returns VaultState.Legacy
        collectUiState()

        act(SettingsScreenAction.OnScreenResumed)

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Hidden)
    }

    @Test
    fun switchTurnedOff_shouldDisableThroughMaintenanceAndLogIt() = runTest {
        collectUiState()
        act(SettingsScreenAction.OnScreenResumed)
        givenEnvelopeVault(isBiometricUnlockDisabled = true)

        act(SettingsScreenAction.UpdateBiometricUnlock(enabled = false))

        coVerify(exactly = 1) { vaultKeyMaintenance.setBiometricUnlockEnabled(false) }
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.BIOMETRIC_UNLOCK_DISABLED)).isTrue()
        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Available(isOn = false))
    }

    @Test
    fun switchTurnedOn_shouldEnableThroughMaintenanceAndLogIt() = runTest {
        givenEnvelopeVault(isBiometricUnlockDisabled = true)
        collectUiState()
        act(SettingsScreenAction.OnScreenResumed)
        givenEnvelopeVault(isBiometricUnlockDisabled = false)

        act(SettingsScreenAction.UpdateBiometricUnlock(enabled = true))

        coVerify(exactly = 1) { vaultKeyMaintenance.setBiometricUnlockEnabled(true) }
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.BIOMETRIC_UNLOCK_ENABLED)).isTrue()
        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Available(isOn = true))
    }

    @Test
    fun switchToggled_whenUnavailable_shouldDoNothing() = runTest {
        givenBiometricClass(BiometricClass.NONE_ENROLLED)
        collectUiState()
        act(SettingsScreenAction.OnScreenResumed)

        act(SettingsScreenAction.UpdateBiometricUnlock(enabled = true))

        coVerify(exactly = 0) { vaultKeyMaintenance.setBiometricUnlockEnabled(true) }
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun switchTurnedOff_whenTheWriteFails_shouldShowTheStoredStateAndLogNothing() = runTest {
        coEvery { vaultKeyMaintenance.setBiometricUnlockEnabled(false) } throws IOException("disk full")
        collectUiState()
        act(SettingsScreenAction.OnScreenResumed)

        act(SettingsScreenAction.UpdateBiometricUnlock(enabled = false))

        assertThat(viewModel.uiState.value.biometricUnlock).isEqualTo(BiometricUnlockSwitch.Available(isOn = true))
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.BIOMETRIC_UNLOCK_DISABLED)).isFalse()
    }

    // endregion
}