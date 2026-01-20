package com.andryoga.safebox.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.di.IsDebug
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository,
    private val analyticsHelper: AnalyticsHelper,
    @param:IsDebug private val isDebug: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigateToHome = MutableStateFlow<Boolean>(false)
    val navigateToHome: StateFlow<Boolean> = _navigateToHome


    init {
        if (isDebug) {
            _uiState.value = _uiState.value.copy(
                password = "Qwerty@@135",
                hint = "This is a hint",
                passwordValidatorState = PasswordValidatorState.PASSWORD_IS_OK,
                isSignupButtonEnabled = true
            )
        }

        uiState.map {
            SignupButtonEnableParams(
                passwordValidatorState = it.passwordValidatorState, hint = it.hint
            )
        }.distinctUntilChanged().onEach { enableParams ->
            _uiState.update {
                it.copy(isSignupButtonEnabled = enableParams.passwordValidatorState == PasswordValidatorState.PASSWORD_IS_OK && enableParams.hint.isNotBlank())
            }
        }.launchIn(viewModelScope)

        uiState.map { it.passwordValidatorState }.distinctUntilChanged()
            .onEach { passwordValidatorState ->
                _uiState.value = _uiState.value.copy(
                    isPasswordFieldError = !(passwordValidatorState == PasswordValidatorState.PASSWORD_IS_OK || passwordValidatorState == PasswordValidatorState.INITIAL_STATE)
                )
            }.launchIn(viewModelScope)
    }

    fun onAction(action: SignupScreenAction) {
        when (action) {
            is SignupScreenAction.OnHintUpdate -> onHintUpdate(hint = action.hint)
            is SignupScreenAction.OnPasswordUpdate -> onPasswordUpdate(password = action.password)
            SignupScreenAction.OnSignupClick -> signup()
        }
    }

    private fun onPasswordUpdate(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password, passwordValidatorState = runPasswordValidator(password = password)
        )
    }

    private fun onHintUpdate(hint: String) {
        _uiState.value = _uiState.value.copy(hint = hint)
    }

    private fun runPasswordValidator(
        password: String = _uiState.value.password
    ): PasswordValidatorState {
        var hasLowerCase = false
        var hasUpperCase = false
        var numericCount = 0
        var specialCharCount = 0

        password.forEach { char ->
            when {
                char.isLowerCase() -> hasLowerCase = true
                char.isUpperCase() -> hasUpperCase = true
                char.isDigit() -> numericCount++
                !char.isLetterOrDigit() -> specialCharCount++
            }
        }
        return when {
            password.isBlank() -> PasswordValidatorState.EMPTY_PASSWORD
            hasLowerCase.not() || hasUpperCase.not() -> {
                PasswordValidatorState.NOT_MIX_CASE
            }

            numericCount < Constants.MIN_NUMERIC_COUNT -> {
                PasswordValidatorState.LESS_NUMERIC_COUNT
            }

            specialCharCount == 0 -> {
                PasswordValidatorState.NO_SPECIAL_CHAR
            }

            password.length < Constants.MIN_PASSWORD_LENGTH -> {
                PasswordValidatorState.SHORT_PASSWORD_LENGTH
            }

            else -> {
                PasswordValidatorState.PASSWORD_IS_OK
            }
        }
    }

    private fun signup() {
        val password = _uiState.value.password
        val hint = _uiState.value.hint

        Timber.i("save password clicked")
        val passwordValidatorState = runPasswordValidator()
        if (passwordValidatorState != PasswordValidatorState.PASSWORD_IS_OK || hint.isBlank()) {
            // ideally flow should NEVER come here bcz we don't allow signup click
            Timber.w("password not ok but user was able to tap on signup button !!")
            analyticsHelper.logEvent(AnalyticsKey.SIGNUP_BLOCKED)
            _uiState.update { it.copy(passwordValidatorState = passwordValidatorState) }
            return
        }

        analyticsHelper.logEvent(AnalyticsKey.SIGN_UP)

        viewModelScope.launch {
            userDetailsRepository.insertUserDetailsData(password, hint)

            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                false
            )
            Timber.i("Added pswrd in db")

            _navigateToHome.emit(true)
        }
    }

    object Constants {
        const val MIN_PASSWORD_LENGTH = 7
        const val MIN_NUMERIC_COUNT = 2
    }

    private data class SignupButtonEnableParams(
        val passwordValidatorState: PasswordValidatorState, val hint: String
    )
}