package com.andryoga.safebox.ui.view.chooseMasterPswrd

import androidx.lifecycle.*
import com.andryoga.safebox.common.Constants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.view.chooseMasterPswrd.PasswordValidationFailureCode.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChooseMasterPswrdViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
    private val userDetailsRepository: UserDetailsRepository
) : ViewModel() {

    object Constants {
        const val minPasswordLength = 8
    }

    private val _isSaveButtonEnabled = MutableLiveData<Boolean>(false)
    val isSaveButtonEnabled: LiveData<Boolean> = _isSaveButtonEnabled

    private val _navigateToHome = MutableLiveData<Boolean>()
    val navigateToHome: LiveData<Boolean> = _navigateToHome

    val pswrd = MutableLiveData<String>("")
    val confirmPswrd = MutableLiveData<String>("")

    val pswrdValidationFailures: LiveData<List<PasswordValidationFailureCode>> =
        Transformations.map(pswrd) { currPswrd ->
            val validationFailures = evaluatePasswordValidationRules(currPswrd)
            _isSaveButtonEnabled.value = validationFailures.isNullOrEmpty()
            return@map validationFailures
        }

    val isBothPasswordsMatch = Transformations.map(confirmPswrd) { confirmPswrdText ->
        val isBothMatch = confirmPswrdText == pswrd.value
        _isSaveButtonEnabled.value =
            pswrd.value?.let { evaluatePasswordValidationRules(it).isNullOrEmpty() }
        return@map isBothMatch
    }

    private fun evaluatePasswordValidationRules(pswrd: String): List<PasswordValidationFailureCode> {
        val list = mutableListOf<PasswordValidationFailureCode>()

        if (pswrd.length <= Constants.minPasswordLength) {
            list.add(LOW_PASSWORD_LENGTH)
        }
        var regex = Regex("[\$&+,:;=?@#|'<>.^*()%!-]")
        var numOfSpecialChar = 0
        regex.findAll(pswrd).iterator().forEach { _ -> numOfSpecialChar++ }

        if (numOfSpecialChar < 2) {
            list.add(LESS_SPECIAL_CHAR_COUNT)
        }

        regex = Regex("[A-Z]")
        val containsUpperCase = regex.containsMatchIn(pswrd)

        regex = Regex("[a-z]")
        val containsLowerCase = regex.containsMatchIn(pswrd)

        if (!containsLowerCase || !containsUpperCase) {
            list.add(NOT_MIX_CASE)
        }

        regex = Regex("[0-9]")
        var numericCount = 0
        regex.findAll(pswrd).iterator().forEach { _ -> numericCount++ }

        if (numericCount <= 2) {
            list.add(LESS_NUMERIC_COUNT)
        }

        for ((index, char) in pswrd.withIndex()) {
            val nextChar = char + 1
            val nextToNextChar = char + 2
            if (pswrd.indexOf("" + nextChar + nextToNextChar) == index + 1) {
                list.add(ALTERNATE_CHAR_FOUND)
                break
            }
        }

        if (pswrd != confirmPswrd.value) {
            list.add(PASSWORD_DO_NOT_MATCH)
        }

        return list
    }

    /*
    * Add password in DB and update pref flag so that next time app asks user to login
    * instead of sign up
    * */
    fun onSaveClick() {
        Timber.i("save password clicked")
        viewModelScope.launch {
            userDetailsRepository.insertUserDetailsData(pswrd.value!!, "ADD HINT IN UI")
            encryptedPreferenceProvider.upsertBooleanPref(IS_SIGN_UP_REQUIRED, false)
            Timber.i("Added pswrd in db")
            _navigateToHome.value = true
        }
    }
}
