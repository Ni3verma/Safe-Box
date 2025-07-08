package com.andryoga.safebox.data.db.docs

data class ViewBankCardData(
    val key: Int,
    val title: String,
    val name: String?,
    val number: String,
    val pin: String?,
    val cvv: String?,
    val expiryDate: String?,
    val notes: String?,
    val creationDate: String,
    val updateDate: String
)
