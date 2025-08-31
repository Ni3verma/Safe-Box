package com.andryoga.composeapp.ui.login

enum class PasswordValidatorState {
    /** initial vale for the state*/
    INITIAL,

    /** password entered is correct and we can navigate to the next screen*/
    VERIFIED,

    /** password entered is incorrect, error message should be displayed */
    INCORRECT
}