package com.andryoga.safebox.common

enum class AnalyticsKey(val eventName: String) {
    SIGNUP_BLOCKED("signup_blocked"),
    SIGN_UP("sign_up"),
    LOGIN_FAILED("login_failed"),
    NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK("notification_permission_rationale_dialog_allow_click"),
    NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK("notification_permission_rationale_dialog_cancel_click"),
    OPEN_GITHUB("open_github"),
    OPEN_PLAY_STORE("open_play_store"),
    EMAIL_FEEDBACK("email_feedback"),
    BACKUP_SELECT_DIR_RESULT("backup_select_dir_result"),
    BACKUP_DATA_SUCCESS("backup_data_success"),
    BACKUP_DATA_FAILURE("backup_data_failure"),
    RESTORE_DATA_SUCCESS("restore_data_success"),
    RESTORE_DATA_FAILURE("restore_data_failure"),
    RESTORE_DATA_WRONG_PASSWORD("restore_data_wrong_password"),
    RESTORE_STARTED("restore_started"),
    NEW_SECURE_NOTE("new_secure_note"),
    NEW_BANK_CARD("new_bank_card"),
    NEW_LOGIN("new_login"),
    NEW_BANK_ACCOUNT("new_bank_account")
}