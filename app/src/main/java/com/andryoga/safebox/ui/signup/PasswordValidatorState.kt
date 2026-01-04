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
            PasswordValidatorState.INITIAL_STATE -> ""
            PasswordValidatorState.EMPTY_PASSWORD -> stringResource(R.string.blank_validation_text)
            PasswordValidatorState.SHORT_PASSWORD_LENGTH -> stringResource(R.string.length_validation_text)
            PasswordValidatorState.NO_SPECIAL_CHAR -> stringResource(R.string.special_char_validation_text)
            PasswordValidatorState.NOT_MIX_CASE -> stringResource(R.string.case_validation_text)
            PasswordValidatorState.LESS_NUMERIC_COUNT -> stringResource(R.string.numeric_validation_text)
            PasswordValidatorState.PASSWORD_IS_OK -> ""
        }
    }
}
