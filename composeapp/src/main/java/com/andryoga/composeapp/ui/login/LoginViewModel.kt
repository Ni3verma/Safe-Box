package com.andryoga.composeapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.composeapp.common.AnalyticsKeys.LOGIN_FAILED
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import com.andryoga.composeapp.worker.BackupDataWorker
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
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
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onAction(action: LoginScreenAction) {
        when (action) {
            is LoginScreenAction.LoginClicked -> onLoginClicked(action.password)
            LoginScreenAction.ShowHintClicked -> getHintFromDb()
            LoginScreenAction.BiometricSuccess -> onBiometricSuccess()
            LoginScreenAction.BiometricAvailable -> onBiometricAvailable()
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
        Timber.i("biometric success")
        viewModelScope.launch {
            onAuthSuccess(withBiometric = true)
        }
    }

    private fun onLoginClicked(password: String) {
        Timber.i("login clicked")
        viewModelScope.launch {
            val isPasswordCorrect = userDetailsRepository.checkPassword(password)
            Timber.i("is password correct: $isPasswordCorrect")

            if (isPasswordCorrect) {
                BackupDataWorker.enqueueRequest(
                    password = password,
                    showBackupStartNotification = false,
                    workManager = workManager,
                    symmetricKeyUtils
                )
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
        Firebase.analytics.logEvent(LOGIN_FAILED, null)
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