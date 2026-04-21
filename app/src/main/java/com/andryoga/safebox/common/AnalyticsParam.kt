package com.andryoga.safebox.common

enum class AnalyticsParam(val paramName: String) {
    // region notification permission dialog
    DO_NOT_ASK_AGAIN("do_not_ask_again"),
    PERMISSION_ASKED_BEFORE("permission_asked_before"),
    REDIRECT_TO_SETTINGS("redirect_to_settings"),

    // endregion
    RESULT("result"),
    MESSAGE("message"),
    VERSION("version")
}