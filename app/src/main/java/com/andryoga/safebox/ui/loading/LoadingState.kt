package com.andryoga.safebox.ui.loading

sealed interface LoadingState {
    object Initial : LoadingState
    object ProceedToLogin : LoadingState
    object ProceedToSignup : LoadingState
}