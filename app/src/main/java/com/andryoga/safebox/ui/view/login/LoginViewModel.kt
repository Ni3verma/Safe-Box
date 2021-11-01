package com.andryoga.safebox.ui.view.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.Constants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.common.Constants.LOGIN_COUNT_WITH_BIOMETRIC
import com.andryoga.safebox.common.Constants.TOTAL_LOGIN_COUNT
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val preferenceProvider: PreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {

    object Constants {
        const val MAX_CONT_BIOMETRIC_LOGINS = 5
        const val ASK_FOR_REVIEW_AFTER_EVERY = 10
    }

    val isSignUpRequired: Boolean =
        encryptedPreferenceProvider.getBooleanPref(IS_SIGN_UP_REQUIRED, true)
    val loginCountWithBiometric: Int =
        preferenceProvider.getIntPref(LOGIN_COUNT_WITH_BIOMETRIC, 0)

    var totalLoginCount: Int = 0

    val pswrd = MutableStateFlow("")
    val hint = MutableStateFlow("")

    init {
        if (BuildConfig.DEBUG) {
            pswrd.value = "Qwerty@@135"
        }
        viewModelScope.launch(Dispatchers.IO) {
            totalLoginCount = preferenceProvider.getIntPref(TOTAL_LOGIN_COUNT, 0)
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
                    canNavigateToHome(0)
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
        canNavigateToHome(loginCountWithBiometric + 1)
    }

    private fun canNavigateToHome(newLoginCountWithBiometric: Int) {
        Timber.i(
            "setting login count with biometric to $newLoginCountWithBiometric" +
                " and login count to ${totalLoginCount + 1}"
        )
        preferenceProvider.upsertIntPref(
            LOGIN_COUNT_WITH_BIOMETRIC,
            newLoginCountWithBiometric
        )
        preferenceProvider.upsertIntPref(
            TOTAL_LOGIN_COUNT,
            totalLoginCount + 1
        )
        _navigateToHome.value = true
    }
}
