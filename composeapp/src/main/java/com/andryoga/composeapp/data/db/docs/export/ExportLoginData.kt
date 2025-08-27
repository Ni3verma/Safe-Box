package com.andryoga.composeapp.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ExportLoginData(
    val title: String,
    val url: String?,
    val password: String?,
    val notes: String?,
    val userId: String,
    val creationDate: Long,
    val updateDate: Long
)
