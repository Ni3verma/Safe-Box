package com.andryoga.safebox.ui.view.home.addNewData.login

import androidx.lifecycle.MutableLiveData

data class AddNewLoginData(
    val title: MutableLiveData<String>,
    val url: MutableLiveData<String?>,
    val userId: MutableLiveData<String>,
    val password: MutableLiveData<String>,
    val notes: MutableLiveData<String?>
) {
    constructor() : this(
        MutableLiveData(""),
        MutableLiveData(""),
        MutableLiveData(""),
        MutableLiveData(""),
        MutableLiveData("")
    )
}
