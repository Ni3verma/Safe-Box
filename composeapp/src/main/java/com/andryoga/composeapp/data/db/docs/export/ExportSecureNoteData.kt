package com.andryoga.composeapp.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ExportSecureNoteData(
    val title: String,
    val notes: String,
    val creationDate: Long,
    val updateDate: Long
)
