package com.andryoga.safebox.ui.view.home.dataDetails.bankAccount

import com.andryoga.safebox.BuildConfig

data class AddNewBankAccountScreenData(
    var title: String = "",
    var accountNo: String = "",
    var customerName: String = "",
    var customerId: String = "",
    var branchCode: String = "",
    var branchName: String = "",
    var branchAddress: String = "",
    var ifscCode: String = "",
    var micrCode: String = "",
    var notes: String = ""
) {
    init {
        if (BuildConfig.DEBUG) {
            title = "HDFC Bank"
            accountNo = "H883830290902"
            customerName = "test"
            customerId = "132455"
            branchCode = "002829"
            branchName = "abc"
            branchAddress = "xyz,punjab"
            ifscCode = "HDFC8938939"
            micrCode = "7339893"
            notes = "fnsf:35235 \nfasgag:546436 \nsafafasf:fasassa\nsfasf=ffaf"
        }
    }
}
