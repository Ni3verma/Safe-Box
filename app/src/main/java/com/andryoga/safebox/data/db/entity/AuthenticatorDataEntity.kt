package com.andryoga.safebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.andryoga.safebox.totp.models.TotpAlgorithm
import java.util.Date

/**
 * Room database entity representing a 2FA TOTP authenticator record stored in the database.
 * The [secretKey] field contains encrypted Base32 secret bytes.
 *
 * [algorithm], [digits] and [period] are the issuer's generation parameters. They are stored in
 * plain text: they are not secret, and encrypting them would make them unqueryable for no benefit.
 * Without them a record scanned from an issuer that does not use the RFC 6238 defaults would
 * silently generate wrong codes forever.
 */
@Entity(tableName = "authenticator_data")
data class AuthenticatorDataEntity(
    @PrimaryKey(autoGenerate = true)
    val key: Int = 0,
    val title: String,
    val secretKey: String,
    val algorithm: TotpAlgorithm,
    val digits: Int,
    val period: Int,
    val creationDate: Date,
    val updateDate: Date,
)
