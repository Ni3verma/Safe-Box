package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ExportBankAccountData(
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
    val creationDate: Long,
    val updateDate: Long
)
