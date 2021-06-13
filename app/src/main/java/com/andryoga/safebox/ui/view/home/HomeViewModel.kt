package com.andryoga.safebox.ui.view.home

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.andryoga.safebox.ui.common.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _isAddNewOptionsExpanded = MutableLiveData<Boolean>(false)
    val isAddNewOptionsExpanded: LiveData<Boolean> = _isAddNewOptionsExpanded

    private val _masterOptionClicked = MutableLiveData<Boolean>(false)
    val masterOptionClicked: LiveData<Boolean> = _masterOptionClicked

    private val _addNewUserDataFabClicked = SingleLiveEvent<Int>()
    val addNewUserDataFabClicked: LiveData<Int> = _addNewUserDataFabClicked

    fun onMasterOptionClick() {
        _isAddNewOptionsExpanded.value = !_isAddNewOptionsExpanded.value!!
        val isCurrentlyExpanded = isAddNewOptionsExpanded.value!!

        _masterOptionClicked.value = isCurrentlyExpanded
    }

    fun onAddNewUserDataFabClick(view: View) {
        Timber.i("add new user data fab clicked, id = ${view.id}")
        _addNewUserDataFabClicked.value = view.id
    }
}
