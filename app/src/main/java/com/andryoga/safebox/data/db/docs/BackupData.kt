package com.andryoga.safebox.data.db.docs

data class BackupData(
    val key: Int,
    val uriString: String,
    val displayPath: String,
    val lastBackupDate: String,
    val createdOn: String,
)
