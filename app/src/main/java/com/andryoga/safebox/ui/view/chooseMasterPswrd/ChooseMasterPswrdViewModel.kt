package com.andryoga.safebox.ui.view.chooseMasterPswrd

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.CommonConstants.IS_SIGN_UP_REQUIRED
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.common.Utils.longestCommonSubstring
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.HINT_IS_SUBSET
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.LESS_NUMERIC_COUNT
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.LESS_SPECIAL_CHAR_COUNT
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.LOW_PASSWORD_LENGTH
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdValidationFailureCode.NOT_MIX_CASE
import com.andryoga.safebox.ui.view.chooseMasterPswrd.ChooseMasterPswrdViewModel.Constants.maxHintSubsetLength
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        MutableStateFlow<ChooseMasterPswrdValidationFailureCode?>(null)
    val validationFailureCode: StateFlow<ChooseMasterPswrdValidationFailureCode?> =
        _validationFailureCode

    private val _navigateToHome = MutableLiveData<Boolean>()
    val navigateToHome: LiveData<Boolean> = _navigateToHome

    val pswrd = MutableStateFlow("")
    val hint = MutableStateFlow("")

    init {
        if (BuildConfig.DEBUG) {
            pswrd.value = "Qwerty@@135"
            hint.value = "first 5 @@ first 3"
        }
    }

    fun evaluateValidationRules() {
        if (evaluateValidationRuleJob.isActive) {
            evaluateValidationRuleJob.cancel()
        }
        evaluateValidationRuleJob = viewModelScope.launch(Dispatchers.IO) {
            _validationFailureCode.value = null
            val pswrd = pswrd.value
            val hint = hint.value

            if (longestCommonSubstring(
                    pswrd.lowercase(),
                    hint.lowercase()
                ) >= maxHintSubsetLength
            ) {
                _validationFailureCode.value = HINT_IS_SUBSET
                return@launch
            }

            var regex = Regex("[a-z]")
            val containsLowerCase = regex.containsMatchIn(pswrd)

            regex = Regex("[A-Z]")
            val containsUpperCase = regex.containsMatchIn(pswrd)

            if (!containsLowerCase || !containsUpperCase) {
                _validationFailureCode.value = NOT_MIX_CASE
                return@launch
            }

            regex = Regex("[0-9]")
            var numericCount = 0
            regex.findAll(pswrd).iterator().forEach { _ -> numericCount++ }

            if (numericCount <= 2) {
                _validationFailureCode.value = LESS_NUMERIC_COUNT
                return@launch
            }

            regex = Regex("[\$&+,:;=?@#|'<>.^*()%!-]")
            var numOfSpecialChar = 0
            regex.findAll(pswrd).iterator().forEach { _ -> numOfSpecialChar++ }

            if (numOfSpecialChar < 2) {
                _validationFailureCode.value = LESS_SPECIAL_CHAR_COUNT
                return@launch
            }

            if (pswrd.length <= Constants.minPasswordLength) {
                _validationFailureCode.value = LOW_PASSWORD_LENGTH
                return@launch
            }
        }
    }

    /*
     * Add password in DB and update pref flag so that next time app asks user to login
     * instead of sign up
     * */
    fun onSaveClick() {
        Timber.i("save password clicked")
        Firebase.analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, null)
        viewModelScope.launch {
            userDetailsRepository.insertUserDetailsData(pswrd.value, hint.value)
            encryptedPreferenceProvider.upsertBooleanPref(IS_SIGN_UP_REQUIRED, false)
            Timber.i("Added pswrd in db")
            _navigateToHome.value = true
        }
    }
}
