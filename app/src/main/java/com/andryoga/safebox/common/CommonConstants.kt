package com.andryoga.safebox.common

object CommonConstants {
    //        SHOULD ONLY be used for simple shared preference
    const val TOTAL_LOGIN_COUNT = "total_login_count"

    // user cannot login with biometric always, it has a limit
    const val ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING = "allowed_biometric_login_count_remaining"
    const val IS_NOTIFICATION_PERMISSION_ASKED_BEFORE = "is_notification_permission_asked_before"
    const val IS_NEVER_ASK_FOR_NOTIFICATION_PERMISSION = "is_never_ask_for_notification_permission"

    //        SHOULD ONLY be user for encrypted shared preference
    const val IS_SIGN_UP_REQUIRED = "is_sign_up_required"

    // crashlytics
    const val CRASHLYTICS_KEY_UID = "uid"

    // Backup file data
    const val VERSION_KEY = "0"
    const val SALT_KEY = "1"
    const val IV_KEY = "2"
    const val CREATION_DATE_KEY = "3"
    const val LOGIN_DATA_KEY = "4"
    const val BANK_ACCOUNT_DATA_KEY = "5"
    const val BANK_CARD_DATA_KEY = "6"
    const val SECURE_NOTE_DATA_KEY = "7"
    const val BACKUP_VERSION = 2

    // Backup params
    const val BACKUP_PARAM_PASSWORD = "0"
    const val BACKUP_PARAM_IS_SHOW_START_NOTIFICATION = "1"

    // Backup metadata
    const val WORKER_NAME_BACKUP_DATA = "BACKUP_SAFE_BOX_DATA_WORK"
    const val MAX_BACKUP_FILES = 5

    // Restore params
    const val RESTORE_PARAM_PASSWORD = "0"
    const val RESTORE_PARAM_FILE_URI = "1"

    // Backup metadata
    const val WORKER_NAME_RESTORE_DATA = "RESTORE_SAFE_BOX_DATA_WORK"

    //        Other
    const val time1Sec = 1000L
    const val time5Sec = 5000L
    const val APP_GITHUB_URL = "https://github.com/Ni3verma/Safe-Box"
    const val APP_PLAYSTORE_LINK =
        "https://play.google.com/store/apps/details?id=com.andryoga.safebox"
}
