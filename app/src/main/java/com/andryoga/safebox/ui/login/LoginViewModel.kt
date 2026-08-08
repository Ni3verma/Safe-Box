package com.andryoga.safebox.ui.login


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.di.IsDebug
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
            LoginScreenAction.OnDeviceSecurityRequiredDialogShown -> {
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_SHOW)
            }

            LoginScreenAction.OnDeviceSecurityRequiredOpenSettingsClicked -> {
                activeSessionManager.setPaused(true)
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK)
            }

            LoginScreenAction.OnDeviceSecurityRequiredDismissClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK)
            }

            LoginScreenAction.OnUpdatePasswordDialogShown -> {
                analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_SHOW)
            }

            LoginScreenAction.OnUpdatePasswordDismissClicked -> {
                analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_CANCEL_CLICK)
            }
        }
    }

    private fun onResetPassword(newPassword: String, hint: String) {
        Timber.i("resetting master password and hint from login screen")
        analyticsHelper.logEvent(AnalyticsKey.UPDATE_PASSWORD_DIALOG_ALLOW_CLICK)
        viewModelScope.launch {
            userDetailsRepository.updatePasswordAndHint(newPassword, hint)
            Timber.i("master password and hint successfully updated from login screen")
            onAuthSuccess(withBiometric = false)
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
            onAuthSuccess(withBiometric = true)
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
                onAuthSuccess(withBiometric = false)
            } else {
                onIncorrectPassword()
            }
        }
    }

    private suspend fun onAuthSuccess(withBiometric: Boolean) {
        userDetailsRepository.onAuthSuccess(withBiometric = withBiometric)
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
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED
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