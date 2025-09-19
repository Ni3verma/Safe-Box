package com.andryoga.composeapp.ui.core.models

import java.util.Date

data class BankAccountData(
    val id: Int?,
    val title: String,
    val accountNo: String,
    val customerName: String?,
    val customerId: String?,
    val branchCode: String?,
    val branchName: String?,
    val branchAddress: String?,
    val ifscCode: String?,
    val micrCode: String?,
    val notes: String?,
    val creationDate: Date,
)
