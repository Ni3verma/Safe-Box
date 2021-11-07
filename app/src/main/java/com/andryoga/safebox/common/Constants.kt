package com.andryoga.safebox.common

object Constants {
    //        SHOULD ONLY be user for simple shared preference
    const val TOTAL_LOGIN_COUNT = "total_login_count"
    const val LOGIN_COUNT_WITH_BIOMETRIC = "login_count_with_biometric"

    //        SHOULD ONLY be user for encrypted shared preference
    const val IS_SIGN_UP_REQUIRED = "is_sign_up_required"

    // crashlytics
    const val CRASHLYTICS_KEY_UID = "uid"

    // export and import
    const val VERSION_KEY = "VERSION"
    const val SALT_KEY = "SALT"
    const val IV_KEY = "IV"
    const val CREATION_DATE_KEY = "CREATION_DATE"
    const val LOGIN_DATA_KEY = "LOGIN_DATA"
    const val BANK_ACCOUNT_DATA_KEY = "BANK_ACCOUNT_DATA"
    const val BANK_CARD_DATA_KEY = "BANK_CARD_DATA"
    const val SECURE_NOTE_DATA_KEY = "SECURE_NOTE_DATA"

    const val EXPORT_IMPORT_VERSION = 1

    //        Other
    const val time1Sec = 1000L
    const val APP_GITHUB_URL = "https://github.com/Ni3verma/Safe-Box"
    const val APP_PLAYSTORE_LINK =
        "https://play.google.com/store/apps/details?id=com.andryoga.safebox"
}
