package com.andryoga.safebox.ui.view.home.child.all

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AllInfoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

}