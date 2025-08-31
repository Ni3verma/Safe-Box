package com.andryoga.composeapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.composeapp.common.CommonConstants.IS_SIGN_UP_REQUIRED
import com.andryoga.composeapp.providers.interfaces.EncryptedPreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
) : ViewModel() {
    private val _startDestination = MutableStateFlow<StartDestination>(StartDestination.Loading)
    val startDestination = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val isSignupRequired =
                encryptedPreferenceProvider.getBooleanPref(IS_SIGN_UP_REQUIRED, true)
            _startDestination.update {
                if (isSignupRequired) {
                    StartDestination.Signup
                } else {
                    StartDestination.Login
                }
            }
        }
    }
}

sealed interface StartDestination {
    object Loading : StartDestination
    object Login : StartDestination
    object Signup : StartDestination
}