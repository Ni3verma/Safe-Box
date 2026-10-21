package com.andryoga.safebox.ui.login


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.AnalyticsSource
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.di.IsDebug
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.worker.BackupDataWorker
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: Lazy<WorkManager>,
    private val symmetricKeyUtils: SymmetricKeyUtils,
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val activeSessionManager: ActiveSessionManager,
    private val deviceSecurityStatus: DeviceSecurityStatus,
    private val sessionKeyHolder: SessionKeyHolder,
    private val keystoreGateway: KeystoreGateway,
    @param:IsDebug private val isDebug: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (isDebug) {
            _uiState.update { it.copy(defaultPassword = "Qwerty@@135") }
        }
    }

    fun onAction(action: LoginScreenAction) {
        when (action) {
            is LoginScreenAction.LoginClicked -> onLoginClicked(action.password)
            LoginScreenAction.ShowHintClicked -> getHintFromDb()
            LoginScreenAction.BiometricSuccess -> onBiometricSuccess()
            LoginScreenAction.BiometricAvailable -> onBiometricAvailable()
            LoginScreenAction.BiometricError -> onBiometricError()
            is LoginScreenAction.OnResetPassword -> onResetPassword(action.newPassword, action.hint)
            LoginScreenAction.ForgotPasswordClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.FORGOT_PASSWORD_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDialogShown -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked -> {
                activeSessionManager.setPaused(true)
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsFailed -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDismissClicked -> {
                logFromLoginScreen(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }

            LoginScreenAction.OnUpdatePasswordDialogShown -> {
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            LoginScreenAction.OnUpdatePasswordDismissClicked -> {
                logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
            }
        }
    }

    /** The same dialog events are raised from Settings; `source` keeps the two apart in Firebase. */
    private fun logFromLoginScreen(key: AnalyticsKey) {
        analyticsHelper.logEvent(key) {
            param(AnalyticsParam.SOURCE, AnalyticsSource.LOGIN.value)
        }
    }

    private fun onResetPassword(newPassword: String, hint: String) {
        Timber.i("resetting master password and hint from login screen")
        logFromLoginScreen(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        viewModelScope.launch {
            // The device credential has already authenticated the user; the session opens first
            // because the hint rewrite below needs the legacy key and a lost key must end in
            // LEGACY_KEY_LOST, not in a failed write.
            if (!openLegacySession()) return@launch
            userDetailsRepository.updatePasswordAndHint(newPassword, hint)
            Timber.i("master password and hint successfully updated from login screen")
            onAuthSuccess(LoginMethod.PASSWORD_RESET)
        }
    }

    private fun onBiometricAvailable() {

        Timber.i("biometric capability is available")
        viewModelScope.launch {
            if (userDetailsRepository.shouldStartBiometricAuthFlow()) {
                _uiState.update {
                    it.copy(
                        canUnlockWithBiometric = true
                    )
                }
            }
        }
    }

    private fun onBiometricSuccess() {
        Timber.i("device security auth success")
        viewModelScope.launch {
            if (!openLegacySession()) return@launch
            onAuthSuccess(LoginMethod.BIOMETRIC)
        }
    }

    private fun onBiometricError() {
        Timber.i("biometric error or cancelled")
        _uiState.update {
            it.copy(
                canUnlockWithBiometric = false
            )
        }
    }

    private fun onLoginClicked(password: String) {
        Timber.i("login clicked")
        viewModelScope.launch {
            val isPasswordCorrect = userDetailsRepository.checkPassword(password)
            Timber.i("is password correct: $isPasswordCorrect")

            if (isPasswordCorrect) {
                // The session must be open before the enqueue below: the worker's password
                // parameter is encrypted through SymmetricKeyUtils, which reads the holder.
                if (!openLegacySession()) return@launch
                if (settingsDataStore.getAutoBackupAfterPasswordLogin()) {
                    Timber.i("enqueuing auto backup request after login with pswrd")
                    BackupDataWorker.enqueueRequest(
                        password = password,
                        showBackupStartNotification = false,
                        workManager = workManager.get(),
                        symmetricKeyUtils
                    )
                } else {
                    Timber.i("auto backup disabled from settings")
                }
                onAuthSuccess(LoginMethod.PASSWORD)
            } else {
                onIncorrectPassword()
            }
        }
    }

    /**
     * Opens the vault session once a credential has been accepted.
     *
     * Every vault is still `crypto_version = 1`, so the session is the legacy Keystore handle. A
     * missing handle is the lost-key state: the records cannot be opened by anyone, the key is
     * never regenerated, and the screen stays on Login.
     *
     * @return `false` when the legacy key is gone and the unlock must not continue.
     */
    // TODO(MR5): a crypto_version = 2 vault unwraps the DEK (setSessionKey) and only the migration fallback opens a legacy session
    private fun openLegacySession(): Boolean {
        val legacyKey = keystoreGateway.legacyVaultKey()
        if (legacyKey == null) {
            Timber.w("legacy vault key is missing; the vault cannot be opened")
            _uiState.update { it.copy(userAuthState = UserAuthState.LEGACY_KEY_LOST) }
            return false
        }
        sessionKeyHolder.setLegacySession(legacyKey)
        return true
    }

    /** Single exit of every unlock path. */
    private suspend fun onAuthSuccess(method: LoginMethod) {
        val loginCount = userDetailsRepository.onAuthSuccess(withBiometric = method == LoginMethod.BIOMETRIC)
        // Telemetry must not be able to stop an unlock: a failed read only drops these params.
        val deviceSecurity = runCatching { deviceSecurityStatus.snapshot() }.getOrNull()
        analyticsHelper.logEvent(AnalyticsKey.LOGIN_SUCCESS) {
            param(AnalyticsParam.LOGIN_METHOD, method.analyticsValue)
            param(AnalyticsParam.LOGIN_COUNT, loginCount)
            if (deviceSecurity != null) {
                param(AnalyticsParam.DEVICE_SECURE, deviceSecurity.isDeviceSecure)
                param(AnalyticsParam.BIOMETRIC_CLASS, deviceSecurity.biometricClass.analyticsValue)
                param(AnalyticsParam.STRONGBOX, deviceSecurity.hasStrongBox)
            }
        }
        _uiState.update {
            it.copy(
                userAuthState = UserAuthState.VERIFIED
            )
        }
    }

    private fun onIncorrectPassword() {
        analyticsHelper.logEvent(AnalyticsKey.LOGIN_FAILED)
        _uiState.update {
            it.copy(
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
                failedLoginAttempts = it.failedLoginAttempts + 1,
            )
        }
    }

    private fun getHintFromDb() {
        viewModelScope.launch {
            Timber.i("getting hint")
            _uiState.update {
                it.copy(
                    hint = userDetailsRepository.getHint() ?: ""
                )
            }
        }
    }
}