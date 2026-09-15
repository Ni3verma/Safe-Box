package com.andryoga.safebox.domain.models.record

/**
 * UI model for a single row on the records list screen.
 *
 * @property totpSecret Base32 seed, populated only for [RecordType.AUTHENTICATOR] rows so the card
 * can derive and roll the one-time code locally. Null for every other record type, which is why it
 * is defaulted rather than required at each mapper call site.
 */
data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val recordType: RecordType,
    val key: String = "${recordType.name}_$id",
    val totpSecret: String? = null,
)