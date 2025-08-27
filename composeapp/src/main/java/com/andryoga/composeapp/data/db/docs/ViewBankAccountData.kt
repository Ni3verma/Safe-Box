package com.andryoga.composeapp.data.db.docs

data class ViewBankAccountData(
    val key: Int,
    val title: String,
    val accountNumber: String,
    val customerName: String?,
    val customerId: String?,
    val branchCode: String?,
    val branchName: String?,
    val branchAddress: String?,
    val ifscCode: String?,
    val micrCode: String?,
    val notes: String?,
    val creationDate: String,
    val updateDate: String
)
