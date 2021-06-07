package com.andryoga.safebox.ui.view.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.Constants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {
    val isSignUpRequired: Boolean =
        encryptedPreferenceProvider.getBooleanPref(IS_SIGN_UP_REQUIRED, true)

    val pswrd = MutableLiveData("Qwerty@@135")

    private val _isWrongPswrdEntered = MutableLiveData<Boolean>()
    val isWrongPswrdEntered: LiveData<Boolean> = _isWrongPswrdEntered

    private val _navigateToHome = MutableLiveData<Boolean>()
    val navigateToHome: LiveData<Boolean> = _navigateToHome

    fun onUnlockClick() {
        Timber.d("unlock clicked, entered pswrd = ${pswrd.value}")
        if (pswrd.value != null) {
            viewModelScope.launch {
                val isPasswordCorrect = userDetailsRepository.checkPassword(pswrd.value!!)
                if (isPasswordCorrect) {
                    Timber.i("correct pswrd entered")
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
}
