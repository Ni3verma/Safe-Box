package com.andryoga.safebox.common

/**
 * Stable values for [AnalyticsParam.SOURCE], identifying which screen raised an event that more
 * than one screen can raise.
 *
 * Kept as an enum rather than loose strings so the same event logged from two places cannot drift
 * into two spellings, which would silently split the funnel in Firebase.
 *
 * @param value Wire value sent to Firebase. Renaming one breaks continuity with already
 * collected data, so treat these as append-only.
 */
enum class AnalyticsSource(val value: String) {
    RECORDS_LIST("records_list"),
    RECORD_DETAIL("record_detail"),
}
