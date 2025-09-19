package com.andryoga.composeapp.ui.core.models

import java.util.Date

data class NoteData(
    val id: Int?,
    val title: String,
    val notes: String,
    val creationDate: Date,
    val updateDate: Date
)
