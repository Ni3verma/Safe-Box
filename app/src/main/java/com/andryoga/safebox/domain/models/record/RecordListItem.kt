package com.andryoga.safebox.domain.models.record

/**
 * UI model for a single row on the records list screen.
 *
 * @property totpSecret Base32 seed, set only for [RecordType.AUTHENTICATOR] rows.
 */
data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val recordType: RecordType,
    val key: String = "${recordType.name}_$id",
    val totpSecret: String? = null,
)