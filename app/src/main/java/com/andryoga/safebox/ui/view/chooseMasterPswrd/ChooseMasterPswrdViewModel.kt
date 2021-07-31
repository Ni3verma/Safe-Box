package com.andryoga.safebox.ui.view.chooseMasterPswrd

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.Constants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.common.Utils.longestCommonSubstring
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.*
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdViewModel.Constants.maxHintSubsetLength
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        const val maxHintSubsetLength = 5
    }

    private var evaluateValidationRuleJob: Job = Job()

    private val _validationFailureCode =
        MutableLiveData<List<ChooseMasterPswrdValidationFailureCode>>(
            emptyList()
        )
    val validationFailureCode: LiveData<List<ChooseMasterPswrdValidationFailureCode>> =
        _validationFailureCode

    private val _navigateToHome = MutableLiveData<Boolean>()
    val navigateToHome: LiveData<Boolean> = _navigateToHome

    val pswrd = MutableLiveData("")
    val confirmPswrd = MutableLiveData("")
    val hint = MutableLiveData("")

    init {
        evaluateValidationRules()
    }

    fun evaluateValidationRules() {
        if (evaluateValidationRuleJob.isActive) {
            evaluateValidationRuleJob.cancel()
        }
        evaluateValidationRuleJob = viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<ChooseMasterPswrdValidationFailureCode>()
            val pswrd = pswrd.value!!
            val confirmPswrd = confirmPswrd.value!!
            val hint = hint.value!!

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

            if (pswrd != confirmPswrd) {
                list.add(PASSWORD_DO_NOT_MATCH)
            }

            if (longestCommonSubstring(
                    pswrd.lowercase(),
                    hint.lowercase()
                ) >= maxHintSubsetLength
            ) {
                list.add(HINT_IS_SUBSET)
            }

            _validationFailureCode.postValue(list)
        }
    }

    /*
    * Add password in DB and update pref flag so that next time app asks user to login
    * instead of sign up
    * */
    fun onSaveClick() {
        Timber.i("save password clicked")
        viewModelScope.launch {
            userDetailsRepository.insertUserDetailsData(pswrd.value!!, hint.value)
            encryptedPreferenceProvider.upsertBooleanPref(IS_SIGN_UP_REQUIRED, false)
            Timber.i("Added pswrd in db")
            _navigateToHome.value = true
        }
    }
}
