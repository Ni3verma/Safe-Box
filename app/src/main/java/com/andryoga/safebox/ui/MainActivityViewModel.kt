package com.andryoga.safebox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.loading.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val encryptedPreferenceProvider: EncryptedPreferenceProvider,
) : ViewModel() {
    private val _loadingState = MutableSharedFlow<LoadingState>()
    val loadingState = _loadingState.asSharedFlow()

    init {
        viewModelScope.launch {
            _loadingState.emit(LoadingState.Initial)
            val isSignupRequired =
                encryptedPreferenceProvider.getBooleanPref(
                    CommonConstants.IS_SIGN_UP_REQUIRED,
                    true
                )
            _loadingState.emit(
                if (isSignupRequired) {
                    LoadingState.ProceedToSignup
                } else {
                    LoadingState.ProceedToLogin
                }
            )
        }
    }
}

