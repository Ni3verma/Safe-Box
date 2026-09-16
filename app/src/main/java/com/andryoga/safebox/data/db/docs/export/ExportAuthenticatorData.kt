package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import com.andryoga.safebox.totp.models.TotpAlgorithm
import kotlinx.serialization.Serializable

/**
 * Data model used for exporting encrypted or decrypted TOTP authenticator records during backup and restore.
 *
 * [algorithm] is a plain string rather than [TotpAlgorithm] so that an unrecognised name fails one
 * record instead of the whole restore.
 */
@Keep
@Serializable
data class ExportAuthenticatorData(
    val title: String,
    val secretKey: String,
    val creationDate: Long,
    val updateDate: Long,
    val algorithm: String,
    val digits: Int,
    val period: Int,
)
