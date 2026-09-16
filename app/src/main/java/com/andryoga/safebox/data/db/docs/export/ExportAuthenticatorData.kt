package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import com.andryoga.safebox.totp.TotpDefaults
import com.andryoga.safebox.totp.models.TotpAlgorithm
import kotlinx.serialization.Serializable

/**
 * Data model used for exporting encrypted or decrypted TOTP authenticator records during backup and restore.
 *
 * [algorithm] is a plain string rather than [TotpAlgorithm] because this is a portable on-disk
 * format: a backup written by a newer build could name an algorithm this build does not know, and
 * deserializing straight into an enum would fail the entire restore instead of that one record.
 * All three generation parameters default to the RFC 6238 values so backups written before they
 * were persisted still deserialize.
 */
@Keep
@Serializable
data class ExportAuthenticatorData(
    val title: String,
    val secretKey: String,
    val creationDate: Long,
    val updateDate: Long,
    val algorithm: String = TotpAlgorithm.SHA1.name,
    val digits: Int = TotpDefaults.DIGITS,
    val period: Int = TotpDefaults.PERIOD_SECONDS,
)
