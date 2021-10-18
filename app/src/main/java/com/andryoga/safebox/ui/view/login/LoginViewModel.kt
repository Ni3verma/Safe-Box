package com.andryoga.safebox.ui.view.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.Constants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.common.Constants.LOGIN_COUNT_WITH_BIOMETRIC
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {

    object Constants {
        const val MAX_CONT_BIOMETRIC_LOGINS = 5
    }

    val isSignUpRequired: Boolean =
        encryptedPreferenceProvider.getBooleanPref(IS_SIGN_UP_REQUIRED, true)
    val loginCountWithBiometric: Int =
        encryptedPreferenceProvider.getIntPref(LOGIN_COUNT_WITH_BIOMETRIC, 0)

    val pswrd = MutableStateFlow("")
    val hint = MutableStateFlow("")

    init {
        if (BuildConfig.DEBUG) {
            pswrd.value = "Qwerty@@135"
        }
    }

    private val _isWrongPswrdEntered = MutableLiveData<Boolean>()
    val isWrongPswrdEntered: LiveData<Boolean> = _isWrongPswrdEntered

    private val _navigateToHome = MutableLiveData<Boolean>()
    val navigateToHome: LiveData<Boolean> = _navigateToHome

    fun getHintFromDb() {
        viewModelScope.launch {
            Timber.i("getting hint")
            val hintText = userDetailsRepository.getHint()
            hint.value = hintText ?: ""
            Timber.d("hint = $hintText")
        }
    }

    fun onUnlockClick() {
        Timber.i("unlock clicked")
        if (pswrd.value != "") {
            viewModelScope.launch {
                val isPasswordCorrect = userDetailsRepository.checkPassword(pswrd.value)
                if (isPasswordCorrect) {
                    Timber.i("correct pswrd entered")
                    encryptedPreferenceProvider.upsertIntPref(LOGIN_COUNT_WITH_BIOMETRIC, 0)
                    _navigateToHome.value = true
                } else {
                    Timber.i("wrong pswrd entered")
                    _isWrongPswrdEntered.value = true
                }
            }
        } else {
            Timber.i("no pswrd entered")
            _isWrongPswrdEntered.value = true
        }
    }

    fun onUnlockedWithBiometric() {
        encryptedPreferenceProvider.upsertIntPref(
            LOGIN_COUNT_WITH_BIOMETRIC,
            loginCountWithBiometric + 1
        )
        Timber.i("continuous login with biometric = ${loginCountWithBiometric + 1}")
    }
}
