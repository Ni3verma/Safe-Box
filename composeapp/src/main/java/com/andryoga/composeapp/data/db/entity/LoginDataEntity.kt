package com.andryoga.composeapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "login_data")
data class LoginDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val url: String?,
    val password: String?,
    val notes: String?,
    val userId: String,
    val creationDate: Date,
    val updateDate: Date
)
