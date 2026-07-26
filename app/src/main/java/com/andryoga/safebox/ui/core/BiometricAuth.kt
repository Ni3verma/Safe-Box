package com.andryoga.safebox.ui.core

import android.content.Context
import androidx.compose.runtime.Composable

fun canAuthenticateUsingBiometric(
    context: Context,
    biometricAuthProvider: BiometricAuthProvider = DefaultBiometricAuthProvider()
): Boolean {
    return biometricAuthProvider.canAuthenticate(context)
}

@Composable
fun BiometricAuthHandler(
    onSuccess: () -> Unit,
    onErrorOrCancel: () -> Unit = {},
    biometricAuthProvider: BiometricAuthProvider = LocalBiometricAuthProvider.current
) {
    biometricAuthProvider.Authenticate(
        onSuccess = onSuccess,
        onErrorOrCancel = onErrorOrCancel
    )
}
