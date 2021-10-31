package com.andryoga.safebox.data.db.docs

data class ViewLoginData(
    val key: Int,
    val title: String,
    val url: String?,
    val password: String?,
    val userId: String,
    val notes: String?,
    val creationDate: String,
    val updateDate: String
)
