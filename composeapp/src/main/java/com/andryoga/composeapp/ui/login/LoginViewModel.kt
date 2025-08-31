package com.andryoga.composeapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.andryoga.composeapp.common.CommonConstants.TOTAL_LOGIN_COUNT
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.composeapp.providers.interfaces.PreferenceProvider
import com.andryoga.composeapp.security.interfaces.SymmetricKeyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val preferenceProvider: PreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository,
    private val workManager: WorkManager,
    private val symmetricKeyUtils: SymmetricKeyUtils
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()
    private var totalLoginCount: Int = 1

    init {
        viewModelScope.launch {
            totalLoginCount = preferenceProvider.getIntPref(TOTAL_LOGIN_COUNT, 1)
        }
    }

    fun onAction(action: LoginScreenAction) {
        when (action) {
            is LoginScreenAction.LoginClicked -> onLoginClicked(action.password)
            LoginScreenAction.ShowHintClicked -> getHintFromDb()
        }
    }

    private fun onLoginClicked(password: String) {
        Timber.i("login clicked")
        viewModelScope.launch {
            val isPasswordCorrect = userDetailsRepository.checkPassword(password)
            Timber.i("is password correct: $isPasswordCorrect")

            val passwordValidatorState = if (isPasswordCorrect) {
                PasswordValidatorState.VERIFIED
            } else {
                PasswordValidatorState.INCORRECT
            }

            _uiState.update {
                it.copy(
                    passwordValidatorState = passwordValidatorState
                )
            }
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

    object Constants {
        private const val MAX_CONT_BIOMETRIC_LOGINS = 5
        private const val ASK_FOR_REVIEW_AFTER_EVERY = 10
    }
}