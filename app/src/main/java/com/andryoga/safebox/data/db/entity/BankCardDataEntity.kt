package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    tableName = "bank_card_data",
    foreignKeys = [
        ForeignKey(
            entity = BankAccountDataEntity::class,
            parentColumns = arrayOf("key"),
            childColumns = arrayOf("linkedBankAccountKey"),
        )
    ]
)
data class BankCardDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val name: String?,
    val number: String,
    val pin: String?,
    val cvv: String,
    val linkedBankAccountKey: Int?,
    val expiryDate: String,
    val notes: String?,
    val creationDate: Date,
    val updateDate: Date
) {
    constructor(
        title: String,
        name: String?,
        number: String,
        pin: String?,
        cvv: String,
        linkedBankAccountKey: Int?,
        expiryDate: String,
        notes: String?,
        creationDate: Date,
        updateDate: Date
    ) : this(
        0,
        title,
        name,
        number,
        pin,
        cvv,
        linkedBankAccountKey,
        expiryDate,
        notes,
        creationDate,
        updateDate
    )
}
