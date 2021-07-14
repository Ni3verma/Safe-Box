package com.andryoga.safebox.ui.view.home

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.andryoga.safebox.ui.common.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    private val _addNewUserDataOptionClicked = SingleLiveEvent<Int>()
    val addNewUserDataOptionClicked: LiveData<Int> = _addNewUserDataOptionClicked

    fun onAddNewUserDataOptionClick(view: View) {
        Timber.i("add new user data fab clicked")
        _addNewUserDataOptionClicked.value = view.id
    }
}
