package com.andryoga.composeapp.ui.loading

sealed interface LoadingState {
    object Initial : LoadingState
    object ProceedToLogin : LoadingState
    object ProceedToSignup : LoadingState
}