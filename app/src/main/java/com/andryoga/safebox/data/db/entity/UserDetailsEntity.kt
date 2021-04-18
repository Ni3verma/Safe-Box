package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "user_details")
data class UserDetailsEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val password: String,
    val hint: String,
    val creationDate: Date,
    val updateDate: Date
) {
    constructor(password: String, hint: String, creationDate: Date, updateDate: Date) : this(
        0,
        password,
        hint,
        creationDate,
        updateDate
    )
}
