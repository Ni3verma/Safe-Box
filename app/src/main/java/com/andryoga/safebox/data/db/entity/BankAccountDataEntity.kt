package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "bank_account_data")
data class BankAccountDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val accountNumber: String,
    val customerName: String?,
    val customerId: String,
    val branchCode: String?,
    val branchName: String?,
    val branchAddress: String?,
    val ifscCode: String,
    val micrCode: String?,
    val notes: String?,
    val creationDate: Date,
    val updateDate: Date
) {
    constructor(
        title: String,
        accountNumber: String,
        customerName: String?,
        customerId: String,
        branchCode: String?,
        branchName: String?,
        branchAddress: String?,
        ifscCode: String,
        micrCode: String?,
        notes: String?,
        creationDate: Date,
        updateDate: Date
    ) : this(
        0,
        title,
        accountNumber,
        customerName,
        customerId,
        branchCode,
        branchName,
        branchAddress,
        ifscCode,
        micrCode,
        notes,
        creationDate,
        updateDate
    )
}
