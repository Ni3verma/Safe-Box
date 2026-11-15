package com.andryoga.safebox.worker.backup

import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * The records of a backup of any format, decrypted and parsed, before the restore replaces the
 * vault with them.
 *
 * A null list means the file carries no payload for that type: an older format that predates it,
 * or a legacy file whose writer stored nothing for an empty type. An empty list means the payload
 * was there and held no records.
 *
 * @property createdAt Creation time as stored in the file; a format-1 file stores a single byte,
 * not an epoch, so this is for logging only.
 * @property undecodableAuthenticatorCount Authenticator elements dropped because they did not
 * match the schema (for example an algorithm a newer build added); counted, never restored.
 */
data class DecodedBackup(
    val createdAt: Long,
    val logins: List<ExportLoginData>?,
    val bankAccounts: List<ExportBankAccountData>?,
    val bankCards: List<ExportBankCardData>?,
    val secureNotes: List<ExportSecureNoteData>?,
    val authenticators: List<ExportAuthenticatorData>?,
    val undecodableAuthenticatorCount: Int,
) {
    /**
     * True when no record type has a payload. A new record type must be added here too,
     * otherwise a backup holding only that type is rejected as empty.
     */
    val hasNoRecordPayload: Boolean
        get() = listOf(logins, bankAccounts, bankCards, secureNotes, authenticators).all { it == null }
}

/**
 * Authenticator records paired with the number of elements that could not be deserialized.
 *
 * @property records Elements that deserialized, not yet checked for semantic usability.
 * @property undecodableCount Elements dropped because they did not match the schema.
 */
internal data class DecodedAuthenticators(
    val records: List<ExportAuthenticatorData>,
    val undecodableCount: Int,
)

/**
 * Decodes authenticator elements one at a time rather than as one list, so a single record written
 * by a future build is dropped on its own instead of aborting a restore that also carries the
 * user's logins, cards and notes.
 *
 * @param array The authenticators JSON array of a backup payload.
 * @return Decoded records and the count of undecodable elements.
 */
internal fun Json.decodeAuthenticatorsLeniently(array: JsonArray): DecodedAuthenticators {
    val records = mutableListOf<ExportAuthenticatorData>()
    var undecodableCount = 0
    for (element in array) {
        try {
            records.add(decodeFromJsonElement(ExportAuthenticatorData.serializer(), element))
        } catch (_: SerializationException) {
            undecodableCount++
        }
    }
    return DecodedAuthenticators(records, undecodableCount)
}
