package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ExportBankCardData(
    val title: String,
    val name: String?,
    val number: String,
    val pin: String?,
    val cvv: String?,
    val expiryDate: String?,
    val notes: String?,
    val creationDate: Long,
    val updateDate: Long
)
