package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andryoga.safebox.data.db.models.VaultMetaKey

/**
 * One row of the `vault_meta` key-value table: the vault's cryptographic state.
 *
 * Holds public parameters and ciphertext only, never key material: the marker of which key the
 * record tables are under, the parked legacy password hash, and later the wrapped data-encryption
 * keys. The keys live in [VaultMetaKey]; the table is useless to a reader without an opener.
 */
@Entity(tableName = "vault_meta")
data class VaultMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
