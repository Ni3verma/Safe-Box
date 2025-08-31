package com.andryoga.composeapp.ui.login

sealed interface LoginScreenAction {
    object ShowHintClicked : LoginScreenAction
    class LoginClicked(val password: String) : LoginScreenAction
}