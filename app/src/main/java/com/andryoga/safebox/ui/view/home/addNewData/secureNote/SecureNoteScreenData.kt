package com.andryoga.safebox.ui.view.home.addNewData.secureNote

import com.andryoga.safebox.BuildConfig

data class SecureNoteScreenData(
    var title: String = "",
    var notes: String = ""
) {
    init {
        if (BuildConfig.DEBUG) {
            title = "git backup keys"
            notes = "fnsf:35235 \nfasgag:546436 \nsafafasf:fasassa\nsfasf=ffaf"
        }
    }
}
