package com.andryoga.composeapp.domain.models.record

import java.util.Date

data class LoginData(
    val id: Int?,
    val title: String,
    val url: String?,
    val userId: String,
    val password: String?,
    val notes: String?,
    val creationDate: Date,
    val updateDate: Date
)
