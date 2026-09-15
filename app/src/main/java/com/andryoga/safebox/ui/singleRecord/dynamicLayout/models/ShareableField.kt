package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

import androidx.annotation.StringRes

/**
 * A single label/value pair that a [com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts.Layout]
 * exposes as safe-to-share content.
 *
 * This exists because the value a record shares is not always the value it stores. For example an
 * authenticator record stores an encrypted Base32 seed but must share the derived one-time code.
 * Keeping this decision inside each layout lets the ViewModel stay record-type agnostic.
 *
 * @param label String resource of the field label shown in the shared text.
 * @param value Already formatted, non-sensitive value to share.
 */
data class ShareableField(
    @param:StringRes val label: Int,
    val value: String,
)
