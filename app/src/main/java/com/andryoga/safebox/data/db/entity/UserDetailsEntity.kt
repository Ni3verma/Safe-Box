package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * The single row describing the vault's owner. Holds no credential since schema v6: the legacy
 * password hash lives in `vault_meta` and the master password only ever appears as a wrapped key.
 * Whether [hint] is ciphertext or plaintext is governed by `vault_meta.crypto_version`.
 */
@Entity(tableName = "user_details")
data class UserDetailsEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int,
    val uid: String,
    val hint: String?,
    val creationDate: Date,
    val updateDate: Date,
)
