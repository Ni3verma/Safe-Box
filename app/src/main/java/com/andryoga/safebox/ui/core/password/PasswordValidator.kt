package com.andryoga.safebox.ui.core.password

import com.andryoga.safebox.ui.signup.PasswordValidatorState

object PasswordValidator {
    object Constants {
        const val MIN_PASSWORD_LENGTH = 7
        const val MIN_NUMERIC_COUNT = 2
    }

    fun validate(password: String): PasswordValidatorState {
        var hasLowerCase = false
        var hasUpperCase = false
        var numericCount = 0
        var specialCharCount = 0

        password.forEach { char ->
            when {
                char.isLowerCase() -> hasLowerCase = true
                char.isUpperCase() -> hasUpperCase = true
                char.isDigit() -> numericCount++
                !char.isLetterOrDigit() -> specialCharCount++
            }
        }
        return when {
            password.isBlank() -> PasswordValidatorState.EMPTY_PASSWORD
            hasLowerCase.not() || hasUpperCase.not() -> {
                PasswordValidatorState.NOT_MIX_CASE
            }

            numericCount < Constants.MIN_NUMERIC_COUNT -> {
                PasswordValidatorState.LESS_NUMERIC_COUNT
            }

            specialCharCount == 0 -> {
                PasswordValidatorState.NO_SPECIAL_CHAR
            }

            password.length < Constants.MIN_PASSWORD_LENGTH -> {
                PasswordValidatorState.SHORT_PASSWORD_LENGTH
            }

            else -> {
                PasswordValidatorState.PASSWORD_IS_OK
            }
        }
    }
}
