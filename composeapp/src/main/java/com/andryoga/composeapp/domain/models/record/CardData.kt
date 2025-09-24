package com.andryoga.composeapp.domain.models.record

import java.util.Date

data class CardData(
    val id: Int?,
    val title: String,
    val name: String?,
    val number: String,
    val expiryDate: String?,
    val cvv: String?,
    val pin: String?,
    val notes: String?,
    val creationDate: Date,
    val updateDate: Date
)
