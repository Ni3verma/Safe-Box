package com.andryoga.composeapp.domain.models.record

import java.util.Date

data class NoteData(
    val id: Int?,
    val title: String,
    val notes: String,
    val creationDate: Date,
)
