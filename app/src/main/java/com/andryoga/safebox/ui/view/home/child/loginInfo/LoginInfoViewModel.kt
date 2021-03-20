package com.andryoga.safebox.ui.view.home.child.loginInfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginInfoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

}