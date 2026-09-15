package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room database entity representing a 2FA TOTP authenticator record stored in the database.
 * The [secretKey] field contains encrypted Base32 secret bytes.
 */
@Entity(tableName = "authenticator_data")
data class AuthenticatorDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int = 0,
    val title: String,
    val secretKey: String,
    val creationDate: Date,
    val updateDate: Date,
)
