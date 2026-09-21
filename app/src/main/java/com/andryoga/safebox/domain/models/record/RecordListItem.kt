package com.andryoga.safebox.domain.models.record

import com.andryoga.safebox.totp.models.TotpConfig

/**
 * UI model for a single row on the records list screen.
 *
 * @property totpConfig Seed and generation parameters, set only for [RecordType.AUTHENTICATOR]
 * rows.
 */
data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val recordType: RecordType,
    val key: String = "${recordType.name}_$id",
    val totpConfig: TotpConfig? = null,
)