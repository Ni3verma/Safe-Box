package com.andryoga.safebox.data.db.docs

data class ViewSecureNoteData(
    val key: Int,
    val title: String,
    val notes: String,
    val creationDate: String,
    val updateDate: String
)
