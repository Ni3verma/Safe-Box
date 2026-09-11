package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Data model used for exporting encrypted or decrypted TOTP authenticator records during backup and restore.
 */
@Keep
@Serializable
data class ExportAuthenticatorData(
    val title: String,
    val secretKey: String,
    val creationDate: Long,
    val updateDate: Long,
)
