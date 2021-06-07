package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "login_data")
data class LoginDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val url: String?,
    val password: String,
    val notes: String?,
    val userId: String,
    val creationDate: Date,
    val updateDate: Date
) {
    constructor(
        title: String,
        url: String?,
        password: String,
        notes: String?,
        userId: String,
        creationDate: Date,
        updateDate: Date
    ) : this(
        0, title, url, password, notes, userId, creationDate, updateDate
    )
}
