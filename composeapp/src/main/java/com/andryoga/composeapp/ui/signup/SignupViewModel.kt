package com.andryoga.composeapp.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.BuildConfig
import com.andryoga.composeapp.common.AnalyticsKeys.SIGNUP_BLOCKED
import com.andryoga.composeapp.common.CommonConstants.IS_SIGN_UP_REQUIRED
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
import com.andryoga.composeapp.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.composeapp.ui.signup.SignupViewModel.Constants.MIN_NUMERIC_COUNT
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (BuildConfig.DEBUG) {
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
            _uiState.value = _uiState.value.copy(
                isSignupButtonEnabled = enableParams.passwordValidatorState == PasswordValidatorState.PASSWORD_IS_OK && enableParams.hint.isNotEmpty()
            )
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
        _uiState.value = _uiState.value.copy(
            hint = hint, passwordValidatorState = runPasswordValidator(hint = hint)
        )
    }

    private fun runPasswordValidator(
        password: String = _uiState.value.password, hint: String = _uiState.value.hint
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

            numericCount < MIN_NUMERIC_COUNT -> {
                PasswordValidatorState.LESS_NUMERIC_COUNT
            }

            specialCharCount == 0 -> {
                PasswordValidatorState.NO_SPECIAL_CHAR
            }

            password.length <= Constants.MIN_PASSWORD_LENGTH -> {
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
        if (passwordValidatorState != PasswordValidatorState.PASSWORD_IS_OK) {
            Timber.w("password not ok but user was able to tap on signup button !!")
            Firebase.analytics.logEvent(SIGNUP_BLOCKED, null)
            _uiState.value = _uiState.value.copy(
                passwordValidatorState = passwordValidatorState
            )
            return
        }

        Firebase.analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, null)

        viewModelScope.launch {
            userDetailsRepository.insertUserDetailsData(password, hint)

            encryptedPreferenceProvider.upsertBooleanPref(IS_SIGN_UP_REQUIRED, false)
            Timber.i("Added pswrd in db")

            // todo: navigate to home screen
        }
    }

    object Constants {
        const val MIN_PASSWORD_LENGTH = 7
        const val MAX_HINT_SUBSET_LENGTH = 5
        const val MIN_NUMERIC_COUNT = 2
    }

    private data class SignupButtonEnableParams(
        val passwordValidatorState: PasswordValidatorState, val hint: String
    )
}