package com.andryoga.safebox.common

enum class AnalyticsKey(val eventName: String) {
    SIGNUP_BLOCKED("signup_blocked"),
    SIGN_UP("sign_up"),
}

@Deprecated("Use AnalyticsKey (enum) instead")
object AnalyticsKeys {
    // event names
    const val LOGIN_FAILED = "login_failed"

    const val BACKUP_SELECT_DIR_RESULT = "backup_select_dir_result"
    const val BACKUP_FAILED = "backup_failed"

    const val RESTORE_STARTED = "restore_started"
    const val RESTORE_SUCCESS = "restore_success"
    const val RESTORE_FAILED = "restore_failed"

    const val NEW_SECURE_NOTE = "new_secure_note"
    const val NEW_BANK_ACCOUNT = "new_bank_account"
    const val NEW_BANK_CARD = "new_bank_card"
    const val NEW_LOGIN = "new_login"

    const val EMAIL_FEEDBACK = "email_feedback"
    const val OPEN_GITHUB = "open_github"
    const val OPEN_PLAY_STORE = "open_play_store"

    const val NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK =
        "notification_permission_rationale_dialog_allow_click"
    const val NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK =
        "notification_permission_rationale_dialog_cancel_click"

    // param names
    const val VERSION = "version"
    const val RESULT = "result"
    const val DO_NOT_ASK_AGAIN = "do_not_ask_again"
    const val PERMISSION_ASKED_BEFORE = "permission_asked_before"
    const val REDIRECT_TO_SETTINGS = "redirect_to_settings"
}
