package com.andryoga.composeapp.ui.signup

enum class PasswordValidatorState {
    INITIAL_STATE,
    EMPTY_PASSWORD,
    SHORT_PASSWORD_LENGTH,
    NO_SPECIAL_CHAR,
    NOT_MIX_CASE,
    LESS_NUMERIC_COUNT,
    HINT_IS_SUBSET,
    PASSWORD_IS_OK
}
