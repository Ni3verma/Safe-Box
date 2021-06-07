package com.andryoga.safebox.ui.view.chooseMasterPswrd

enum class PasswordValidationFailureCode {
    LOW_PASSWORD_LENGTH,
    LESS_SPECIAL_CHAR_COUNT,
    NOT_MIX_CASE,
    LESS_NUMERIC_COUNT,
    ALTERNATE_CHAR_FOUND,
    PASSWORD_DO_NOT_MATCH
}
