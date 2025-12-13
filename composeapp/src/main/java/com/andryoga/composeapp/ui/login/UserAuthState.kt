package com.andryoga.composeapp.ui.login

enum class UserAuthState {
    /** initial vale for the state*/
    INITIAL,

    /** password entered is correct or user has verified with biometric and we can navigate to the next screen*/
    VERIFIED,

    /** error message should be displayed */
    INCORRECT_PASSWORD_ENTERED
}