package com.andryoga.safebox.common

enum class AnalyticsParam(val paramName: String) {
    // region notification permission dialog
    DO_NOT_ASK_AGAIN("do_not_ask_again"),
    PERMISSION_ASKED_BEFORE("permission_asked_before"),
    REDIRECT_TO_SETTINGS("redirect_to_settings"),

    // endregion
    RESULT("result"),
    MESSAGE("message"),
    VERSION("version"),
    IS_ENABLED("is_enabled"),
    COUNT("count"),
    SOURCE("source"),
    REASON("reason"),
    // lower-case record type name
    RECORD_TYPE("record_type"),
    HAD_INPUT("had_input"),
    // how the vault was unlocked
    LOGIN_METHOD("login_method"),
    // 1-based ordinal of a login for this install
    LOGIN_COUNT("login_count"),
    // lower-case field id name, never a value
    FIELD("field"),

    // region envelope-encryption readiness (docs/analytics/envelope-readiness.md)
    // KeyguardManager.isDeviceSecure at unlock: a PIN, pattern or password exists
    DEVICE_SECURE("device_secure"),
    // BiometricClass.analyticsValue at unlock
    BIOMETRIC_CLASS("biometric_class"),
    // FEATURE_STRONGBOX_KEYSTORE present
    STRONGBOX("strongbox"),
    // simple class name of a caught exception, never its message
    EXCEPTION("exception"),
    // the Room database file already existed when a Keystore key had to be generated
    // TODO(MR3): delete with KEYSTORE_KEY_CREATED, its only event; archive the GA dimension after the read-back.
    DB_EXISTS("db_exists"),
    // records across all five types
    RECORD_COUNT("record_count"),
    // Room database file plus its WAL, in KiB
    DB_SIZE_KB("db_size_kb"),
    // free space on the app-data partition, in MiB
    FREE_SPACE_MB("free_space_mb"),
    // wall-clock duration of a backup or restore, in milliseconds
    DURATION_MS("duration_ms"),
    // size of the backup file just written, in KiB
    SIZE_KB("size_kb"),
    // a backup or restore worker was RUNNING when the away timeout fired
    WORKER_RUNNING("worker_running"),
    // endregion
}