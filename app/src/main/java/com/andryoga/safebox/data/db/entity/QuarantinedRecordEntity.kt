package com.andryoga.safebox.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * A record row the crypto migration could not read under the legacy key, preserved byte for byte.
 *
 * Such a row is unreadable before the migration as well. Instead of aborting the upgrade or
 * deleting bytes that cannot be proven worthless, the migration moves the row here as it was
 * stored - every encrypted column still ciphertext under the legacy key - and the legacy Keystore
 * alias is kept as long as this table is non-empty. Never exported, never rendered; cleared only
 * by *Erase everything and start over*.
 */
@Entity(tableName = "quarantined_record")
data class QuarantinedRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Source table name: `login_data`, `bank_account_data`, `bank_card_data`, `secure_note_data` or `authenticator_data`. */
    @ColumnInfo(name = "source_table")
    val sourceTable: String,
    /** The row's primary key in [sourceTable]. */
    @ColumnInfo(name = "source_key")
    val sourceKey: Int,
    /** The row exactly as stored, as a JSON object of column name to stored value. */
    @ColumnInfo(name = "row_json")
    val rowJson: String,
    /** Why the row could not be read: `aead_bad_tag` or `malformed_ciphertext`. */
    val reason: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Date,
)
