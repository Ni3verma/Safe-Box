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
    title: String? = null,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onErrorOrCancel: () -> Unit = {},
    biometricAuthProvider: BiometricAuthProvider = LocalBiometricAuthProvider.current
) {
    biometricAuthProvider.Authenticate(
        title = title,
        subtitle = subtitle,
        onSuccess = onSuccess,
        onErrorOrCancel = onErrorOrCancel
    )
}

