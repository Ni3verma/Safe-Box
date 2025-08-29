package com.andryoga.composeapp.ui.signup

sealed interface SignupScreenAction {
    data class OnPasswordUpdate(val password: String) : SignupScreenAction
    data class OnHintUpdate(val hint: String) : SignupScreenAction
    object OnSignupClick : SignupScreenAction
}