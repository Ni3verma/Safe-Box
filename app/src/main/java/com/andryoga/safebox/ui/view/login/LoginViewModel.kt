package com.andryoga.safebox.ui.view.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.andryoga.safebox.common.Constants.Companion.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.common.Constants.Companion.MASTER_PSWRD
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider
) : ViewModel() {
    val isSignUpRequired: Boolean =
        encryptedPreferenceProvider.getBooleanPref(IS_SIGN_UP_REQUIRED, true)

    val pswrd = MutableLiveData<String>("")

    private val _isWrongPswrdEntered = MutableLiveData<Boolean>()
    val isWrongPswrdEntered: LiveData<Boolean> = _isWrongPswrdEntered

    fun onUnlockClick() {
        Timber.d("unlock clicked, entered pswrd = ${pswrd.value}")
        val masterPswrd = encryptedPreferenceProvider.getStringPref(MASTER_PSWRD, null)
        if (masterPswrd == pswrd.value) {
            Timber.i("correct pswrd entered")
        } else {
            Timber.i("wrong pswrd entered")
            _isWrongPswrdEntered.value = true
        }
    }
}