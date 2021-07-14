package com.andryoga.safebox.ui.view.home.addNewData.login

import com.andryoga.safebox.BuildConfig

data class AddNewLoginScreenData(
    var title: String = "",
    var url: String? = null,
    var userId: String = "",
    var password: String = "",
    var notes: String? = null
) {
    init {
        if (BuildConfig.DEBUG) {
            title = "google"
            url = "google.com"
            userId = "test123"
            password = "password@#dsk35"
            notes = "fnsf:35235 \nfasgag:546436 \nsafafasf:fasassa\nsfasf=ffaf"
        }
    }
}
