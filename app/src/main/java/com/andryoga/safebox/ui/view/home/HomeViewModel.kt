package com.andryoga.safebox.ui.view.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _isAddNewOptionsExpanded = MutableLiveData<Boolean>(false)
    val isAddNewOptionsExpanded: LiveData<Boolean> = _isAddNewOptionsExpanded

    private val _masterOptionClicked = MutableLiveData<Boolean>(false)
    val masterOptionClicked: LiveData<Boolean> = _masterOptionClicked

    fun onMasterOptionClick() {
        _isAddNewOptionsExpanded.value = !_isAddNewOptionsExpanded.value!!
        val isCurrentlyExpanded = isAddNewOptionsExpanded.value!!

        _masterOptionClicked.value = isCurrentlyExpanded
    }
}
