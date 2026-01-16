package com.andryoga.safebox.ui.signup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R

enum class PasswordValidatorState {
    INITIAL_STATE,
    EMPTY_PASSWORD,
    SHORT_PASSWORD_LENGTH,
    NO_SPECIAL_CHAR,
    NOT_MIX_CASE,
    LESS_NUMERIC_COUNT,
    PASSWORD_IS_OK;

    @Composable
    fun getUiText(): String {
        return when (this) {
            INITIAL_STATE -> ""
            EMPTY_PASSWORD -> stringResource(R.string.blank_validation_text)
            SHORT_PASSWORD_LENGTH -> stringResource(R.string.length_validation_text)
            NO_SPECIAL_CHAR -> stringResource(R.string.special_char_validation_text)
            NOT_MIX_CASE -> stringResource(R.string.case_validation_text)
            LESS_NUMERIC_COUNT -> stringResource(R.string.numeric_validation_text)
            PASSWORD_IS_OK -> ""
        }
    }
}
