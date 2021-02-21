package com.andryoga.safebox.ui.view.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val preferenceProvider: PreferenceProvider
) : ViewModel() {

}