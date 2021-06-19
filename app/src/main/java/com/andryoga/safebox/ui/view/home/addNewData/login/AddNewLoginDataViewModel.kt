package com.andryoga.safebox.ui.view.home.addNewData.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AddNewLoginDataViewModel @Inject constructor() : ViewModel() {
    val addNewLoginData = AddNewLoginData()
    fun onSaveClick() {
        Timber.i("save clicked, adding login data in db")
    }
}
