package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "secure_note_data")
data class SecureNoteDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val title: String,
    val notes: String,
    val creationDate: Date,
    val updateDate: Date
)
