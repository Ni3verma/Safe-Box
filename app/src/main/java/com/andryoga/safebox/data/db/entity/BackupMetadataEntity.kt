package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "backup_metadata")
data class BackupMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int = 1,
    val uriString: String,
    val displayPath: String,
    val lastBackupDate: Date?,
    val createdOn: Date
)
