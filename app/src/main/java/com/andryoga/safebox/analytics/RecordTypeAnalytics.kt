package com.andryoga.safebox.analytics

import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.domain.models.record.RecordType

/**
 * Wire value of a [RecordType] for [AnalyticsParam.RECORD_TYPE]: the enum name in lower case
 * (`login`, `card`, `bank_account`, `note`, `authenticator`).
 *
 * One function rather than `name.lowercase()` at each call site, so every event that carries a
 * record type spells it the same way and a Firebase breakdown by this parameter never splits one
 * type across two values. Renaming an enum entry renames the value, so treat the entries as
 * append-only once an event has shipped.
 */
fun RecordType.toAnalyticsValue(): String = name.lowercase()
