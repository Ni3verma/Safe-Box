package com.andryoga.safebox.ui.view.home.addNewData.bankCard

data class AddNewBankCardScreenData(
    var title: String = "",
    var name: String? = null,
    var number: String = "",
    var expiryDate: String = "",
    var pin: String? = null,
    var cvv: String = "",
    var linkedBankAccount: Int? = null,
    var notes: String? = null
)
