package com.andryoga.safebox.data.db.docs.export

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * The encrypted payload of a format-4 backup: one JSON document holding every record type.
 *
 * The writer always fills every list, empty when the vault has no records of that type. A missing
 * field means the writer predates the type.
 */
@Keep
@Serializable
data class ExportBackupPayload(
    val logins: List<ExportLoginData>? = null,
    val bankAccounts: List<ExportBankAccountData>? = null,
    val bankCards: List<ExportBankCardData>? = null,
    val secureNotes: List<ExportSecureNoteData>? = null,
    val authenticators: List<ExportAuthenticatorData>? = null,
)
