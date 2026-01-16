package com.andryoga.safebox.data.db.docs

import java.util.Date

data class SearchSecureNoteData(
    val key: Int,
    val title: String,
    val creationDate: Date
)
