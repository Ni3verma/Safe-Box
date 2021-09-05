package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    tableName = "bank_card_data"
)
data class BankCardDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val name: String?,
    val number: String,
    val pin: String?,
    val cvv: String,
    val expiryDate: String,
    val notes: String?,
    val creationDate: Date,
    val updateDate: Date
)
