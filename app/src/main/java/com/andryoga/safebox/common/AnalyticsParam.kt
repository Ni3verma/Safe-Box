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
    // a RecordType, as RecordType.toAnalyticsValue() renders it
    RECORD_TYPE("record_type"),
    // whether the user changed anything in a form before leaving it
    HAD_INPUT("had_input"),
    // how the vault was unlocked, a LoginMethod.analyticsValue
    METHOD("method"),
    // 1-based ordinal of a login for this install
    LOGIN_COUNT("login_count"),
    // a FieldId name in lower case, never a field's value
    FIELD("field"),
}