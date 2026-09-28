package com.andryoga.safebox.common

enum class AnalyticsKey(val eventName: String) {
    SIGNUP_BLOCKED("signup_blocked"),
    SIGN_UP("sign_up"),
    LOGIN_FAILED("login_failed"),
    NOTIFICATION_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK("notif_perm_dialog_allow_click"),
    NOTIFICATION_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK("notif_perm_dialog_cancel_click"),
    OPEN_GITHUB("open_github"),
    OPEN_PLAY_STORE("open_play_store"),
    EMAIL_FEEDBACK("email_feedback"),
    BACKUP_STARTED("backup_started"),
    BACKUP_SELECT_DIR_RESULT("backup_select_dir_result"),
    BACKUP_DATA_SUCCESS("backup_data_success"),
    // logged with AnalyticsParam.REASON (a BackupFailureReason name) and AnalyticsParam.MESSAGE
    BACKUP_DATA_FAILURE("backup_data_failure"),
    // backup ran with an empty vault, so no file was written. Kept out of BACKUP_DATA_FAILURE.
    BACKUP_DATA_NOTHING_TO_BACKUP("backup_data_nothing_to_backup"),
    // manual backup dialog showed a failure outcome; logged with AnalyticsParam.REASON
    BACKUP_FAILURE_DIALOG_SHOW("backup_failure_dialog_show"),
    RESTORE_DATA_SUCCESS("restore_data_success"),
    RESTORE_DATA_FAILURE("restore_data_failure"),
    RESTORE_DATA_WRONG_PASSWORD("restore_data_wrong_password"),
    RESTORE_DATA_BACKUP_TOO_NEW("restore_data_backup_too_new"),
    RESTORE_DATA_BACKUP_EMPTY("restore_data_backup_empty"),
    RESTORE_STARTED("restore_started"),
    // logged with AnalyticsParam.COUNT when a restore drops authenticator records whose stored
    // seed is not decodable Base32. A non-zero count means backup data and the current Base32
    // validation have drifted apart.
    RESTORE_INVALID_AUTHENTICATOR_SKIPPED("restore_invalid_authenticator_skipped"),
    NEW_SECURE_NOTE("new_secure_note"),
    NEW_BANK_CARD("new_bank_card"),
    NEW_LOGIN("new_login"),
    NEW_BANK_ACCOUNT("new_bank_account"),
    NEW_AUTHENTICATOR("new_authenticator"),
    DEVICE_SECURITY_REQUIRED_DIALOG_SHOW("device_security_required_dialog_show"),
    DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_CLICK("device_sec_dialog_open_settings_click"),
    DEVICE_SECURITY_REQUIRED_DIALOG_OPEN_SETTINGS_FAILURE("device_sec_dialog_open_settings_fail"),
    DEVICE_SECURITY_REQUIRED_DIALOG_CANCEL_CLICK("device_sec_dialog_cancel_click"),
    UPDATE_PASSWORD_DIALOG_SHOW("update_password_dialog_show"),
    UPDATE_PASSWORD_DIALOG_ALLOW_CLICK("update_password_dialog_allow_click"),
    UPDATE_PASSWORD_DIALOG_CANCEL_CLICK("update_password_dialog_cancel_click"),
    CAMERA_PERMISSION_RATIONALE_DIALOG_SHOW("camera_perm_dialog_show"),
    CAMERA_PERMISSION_RATIONALE_DIALOG_ALLOW_CLICK("camera_perm_dialog_allow_click"),
    CAMERA_PERMISSION_RATIONALE_DIALOG_CANCEL_CLICK("camera_perm_dialog_cancel_click"),
    CAMERA_PERMISSION_SETTINGS_OPEN_CLICK("camera_perm_settings_open_click"),
    CAMERA_PERMISSION_RESULT("camera_perm_result"),
    QR_SCANNER_SHOW("qr_scanner_show"),
    QR_SCANNER_TORCH_TOGGLE("qr_scanner_torch_toggle"),
    QR_SCANNER_SUCCESS("qr_scanner_success"),
    QR_SCANNER_CANCEL("qr_scanner_cancel"),
    QR_SCANNER_MANUAL_CLICK("qr_scanner_manual_click"),
    QR_SCANNER_UNSUPPORTED_DIALOG_SHOW("qr_scanner_unsupported_show"),
    QR_SCANNER_UNSUPPORTED_DIALOG_DISMISS("qr_scanner_unsupported_dismiss"),
    // logged from both the records list and the record detail screen, told apart by
    // AnalyticsParam.SOURCE. See AnalyticsSource.
    AUTHENTICATOR_COPY_CLICK("authenticator_copy_click"),
    CLIPBOARD_AUTO_CLEARED("clipboard_auto_cleared"),

    // the clipboard moved on to a clip this app did not write, so it was left untouched.
    CLIPBOARD_AUTO_CLEAR_SKIPPED("clipboard_auto_clear_skipped"),

    // In-app updates. The update check re-emits on every launch, so each event is bounded by a
    // persisted version, a state transition or a per-process guard. See
    // docs/architecture/in-app-updates.md before adding one.
    IN_APP_UPDATE_FLOW_SHOW("in_app_update_flow_show"),
    IN_APP_UPDATE_FLOW_ACCEPT("in_app_update_flow_accept"),
    IN_APP_UPDATE_FLOW_CANCEL("in_app_update_flow_cancel"),
    IN_APP_UPDATE_FLOW_FAILED("in_app_update_flow_failed"),
    IN_APP_UPDATE_DOWNLOADED("in_app_update_downloaded"),
    IN_APP_UPDATE_RESTART_SNACKBAR_SHOW("in_app_update_restart_snackbar_show"),
    IN_APP_UPDATE_RESTART_SNACKBAR_CLICK("in_app_update_restart_snackbar_click"),

    // ✕ tap or a programmatic dismiss, e.g. ClipboardActions on API < 33. Not a CANCEL_CLICK
    // because it does not always mean the user acted.
    IN_APP_UPDATE_RESTART_SNACKBAR_DISMISSED("in_app_update_restart_snackbar_dismissed"),

    // the install of a downloaded update was started without asking, because the vault was
    // locked: login screen, cold start or away timeout. Once per process.
    IN_APP_UPDATE_AUTO_COMPLETE("in_app_update_auto_complete"),
}