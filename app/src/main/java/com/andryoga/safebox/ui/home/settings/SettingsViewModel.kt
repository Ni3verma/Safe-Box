package com.andryoga.safebox.ui.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.common.Utils.launchVaultOperation
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import com.andryoga.safebox.data.repository.interfaces.VaultState
import com.andryoga.safebox.security.envelope.ChangeMasterPasswordUseCase
import com.andryoga.safebox.security.envelope.VaultKeyMaintenance
import com.andryoga.safebox.security.interfaces.BiometricClass
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SessionLockedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val sessionKeyHolder: SessionKeyHolder,
    private val vaultRepository: VaultRepository,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val vaultKeyMaintenance: VaultKeyMaintenance,
    private val changeMasterPasswordUseCase: ChangeMasterPasswordUseCase,
) : ViewModel() {

    // Driving state: the flow step, the switch and the last result. Preferences and the session
    // kind are combined in below so the screen reads one snapshot.
    private val screenState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.settingsFlow,
        screenState,
        sessionKeyHolder.isUnlocked,
    ) { settings, screen, isUnlocked ->
        screen.copy(
            settings = settings,
            isLegacySession = isUnlocked && sessionKeyHolder.isLegacySession,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(),
    )

    fun onScreenAction(action: SettingsScreenAction) {
        when (action) {
            is SettingsScreenAction.UpdatePrivacy -> updatePrivacy(action.enabled)
            is SettingsScreenAction.UpdateAutoBackupAfterLogin -> updateAutoBackupAfterPasswordLogin(
                action.enabled
            )

            is SettingsScreenAction.UpdateAwayTimeout -> updateAwayTimeout(action.timeout)
            is SettingsScreenAction.UpdatePasswordAfterXBiometric -> updatePasswordAfterXBiometricLogin(
                action.limit
            )

            is SettingsScreenAction.UpdateBiometricUnlock -> updateBiometricUnlock(action.enabled)
            SettingsScreenAction.OnScreenResumed -> viewModelScope.launch { refreshBiometricUnlock() }

            is SettingsScreenAction.ChangePasswordClicked -> onChangePasswordClicked(action.canUseDeviceAuth)
            SettingsScreenAction.OnDeviceAuthSucceeded -> onGatePassed(ChangePasswordGate.DEVICE_AUTH)
            SettingsScreenAction.OnDeviceAuthFailedOrCancelled -> setStep(ChangePasswordStep.CurrentPassword())
            is SettingsScreenAction.OnCurrentPasswordSubmitted -> onCurrentPasswordSubmitted(action.password)
            SettingsScreenAction.OnCurrentPasswordDialogShown -> {
                analyticsHelper.logEvent(AnalyticsKey.CURRENT_PASSWORD_DIALOG_SHOW)
            }
            SettingsScreenAction.OnCurrentPasswordDismissed -> onCurrentPasswordDismissed()

            is SettingsScreenAction.OnUpdateMasterPassword -> updateMasterPassword(
                action.newPassword,
                action.hint
            )

            SettingsScreenAction.OnPasswordChangeResultShown -> {
                screenState.update { it.copy(passwordChangeResult = null) }
            }

            SettingsScreenAction.OpenGithubProject -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_GITHUB)
            }

            SettingsScreenAction.OpenBuyMeACoffee -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_BUY_ME_A_COFFEE)
            }

            SettingsScreenAction.ReviewApp -> {
                analyticsHelper.logEvent(AnalyticsKey.OPEN_PLAY_STORE)
            }

            SettingsScreenAction.SendFeedback -> {
                analyticsHelper.logEvent(AnalyticsKey.EMAIL_FEEDBACK)
            }

            SettingsScreenAction.OnUpdatePasswordDialogShown -> {
                logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            SettingsScreenAction.OnUpdatePasswordDismissClicked -> {
                logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
                setStep(null)
            }
        }
    }

    /** The same dialog events are raised from the login screen; `source` keeps the two apart. */
    private fun logFromSettingsScreen(key: AnalyticsKey) {
        analyticsHelper.logEvent(key) {
            param(AnalyticsParam.SOURCE, AnalyticsSource.SETTINGS.value)
        }
    }

    private fun updatePrivacy(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updatePrivacy(enabled)
    }

    private fun updateAwayTimeout(value: Int) = viewModelScope.launch {
        settingsDataStore.updateAwayTimeout(value)
    }

    private fun updateAutoBackupAfterPasswordLogin(value: Boolean) =
        viewModelScope.launch {
            settingsDataStore.updateAutoBackupAfterPasswordLogin(value)
        }

    private fun updatePasswordAfterXBiometricLogin(value: Int) =
        viewModelScope.launch {
            settingsDataStore.updatePasswordAfterXBiometricLogin(value)
        }

    // region fingerprint-unlock switch

    private suspend fun refreshBiometricUnlock() {
        val vaultState = vaultRepository.getVaultState()
        val switch = when {
            sessionKeyHolder.isLegacySession || vaultState !is VaultState.Envelope -> BiometricUnlockSwitch.Hidden
            else -> when (deviceSecurityStatus.snapshot().biometricClass) {
                BiometricClass.NO_HARDWARE -> BiometricUnlockSwitch.Hidden
                BiometricClass.STRONG -> BiometricUnlockSwitch.Available(isOn = !vaultState.isBiometricUnlockDisabled)
                BiometricClass.WEAK_ONLY,
                BiometricClass.NONE_ENROLLED,
                BiometricClass.UNAVAILABLE,
                -> BiometricUnlockSwitch.Unavailable
            }
        }
        screenState.update { it.copy(biometricUnlock = switch) }
    }

    private fun updateBiometricUnlock(enabled: Boolean) {
        val current = screenState.value.biometricUnlock as? BiometricUnlockSwitch.Available ?: return
        if (current.isOn == enabled) return
        screenState.update { it.copy(biometricUnlock = BiometricUnlockSwitch.Available(isOn = enabled)) }
        viewModelScope.launch {
            try {
                vaultKeyMaintenance.setBiometricUnlockEnabled(enabled)
                analyticsHelper.logEvent(
                    if (enabled) AnalyticsKey.BIOMETRIC_UNLOCK_ENABLED else AnalyticsKey.BIOMETRIC_UNLOCK_DISABLED,
                )
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Timber.e(e, "fingerprint unlock switch failed (enabled = %s)", enabled)
            }
            // Re-read so the switch shows what was stored, also after a failure.
            refreshBiometricUnlock()
        }
    }

    // endregion

    // region change master password

    private fun setStep(step: ChangePasswordStep?) {
        screenState.update { it.copy(changePasswordStep = step) }
    }

    private fun onChangePasswordClicked(canUseDeviceAuth: Boolean) {
        // The entry is disabled in a legacy session; a step already on screen owns the flow.
        if (sessionKeyHolder.isLegacySession || screenState.value.changePasswordStep != null) return
        setStep(
            if (canUseDeviceAuth) ChangePasswordStep.DeviceAuthentication else ChangePasswordStep.CurrentPassword(),
        )
    }

    private fun onCurrentPasswordSubmitted(password: String) {
        val step = screenState.value.changePasswordStep as? ChangePasswordStep.CurrentPassword ?: return
        if (step.isVerifying) return
        analyticsHelper.logEvent(AnalyticsKey.CURRENT_PASSWORD_DIALOG_CONFIRM_CLICK)
        setStep(ChangePasswordStep.CurrentPassword(isVerifying = true))
        viewModelScope.launch {
            val isVerified = changeMasterPasswordUseCase.verifyCurrentPassword(password.toCharArray())
            // Cancel may have closed the dialog while the check ran; its answer is then moot.
            if (screenState.value.changePasswordStep !is ChangePasswordStep.CurrentPassword) return@launch
            if (isVerified) {
                onGatePassed(ChangePasswordGate.MASTER_PASSWORD)
            } else {
                setStep(ChangePasswordStep.CurrentPassword(isIncorrect = true))
            }
        }
    }

    private fun onCurrentPasswordDismissed() {
        analyticsHelper.logEvent(AnalyticsKey.CURRENT_PASSWORD_DIALOG_CANCEL_CLICK)
        setStep(null)
    }

    private fun onGatePassed(gate: ChangePasswordGate) {
        analyticsHelper.logEvent(AnalyticsKey.CHANGE_PASSWORD_GATE_PASSED) {
            param(AnalyticsParam.GATE, gate.wireName)
        }
        setStep(ChangePasswordStep.NewPassword)
    }

    private fun updateMasterPassword(newPassword: String, hint: String) {
        // Only the gate leads here; this also drops a second Save tap once the step is cleared.
        if (screenState.value.changePasswordStep != ChangePasswordStep.NewPassword) return
        Timber.i("updating master password and hint from settings screen")
        logFromSettingsScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        setStep(null)
        // Reads the session DEK: a lock mid-call ends silently and the HomeGraph gate leaves.
        viewModelScope.launchVaultOperation {
            val result = try {
                changeMasterPasswordUseCase.changePassword(newPassword.toCharArray(), hint)
                PasswordChangeResult.SUCCESS
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionLockedException) {
                throw e
            } catch (e: Exceptions.DebugFatalException) {
                // A debug-build assertion from below; reporting it as a failed change would hide it.
                throw e
            } catch (e: Exception) {
                Timber.e(e, "master password change failed")
                PasswordChangeResult.FAILURE
            }
            screenState.update { it.copy(passwordChangeResult = result) }
        }
    }

    // endregion
}