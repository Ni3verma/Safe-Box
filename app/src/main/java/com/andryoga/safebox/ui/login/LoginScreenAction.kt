package com.andryoga.safebox.ui.login

sealed interface LoginScreenAction {
    object ShowHintClicked : LoginScreenAction
    class LoginClicked(val password: String) : LoginScreenAction
    object BiometricSuccess : LoginScreenAction

    /**
     * device supports Biometric auth, run business logic to determine if biometric auth should be triggered
     * */
    object BiometricAvailable : LoginScreenAction
}