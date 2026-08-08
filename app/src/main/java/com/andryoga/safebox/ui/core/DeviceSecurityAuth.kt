package com.andryoga.safebox.ui.core

import android.content.Context
import androidx.compose.runtime.Composable
import timber.log.Timber

fun canAuthenticateUsingDeviceSecurity(
    context: Context,
    biometricAuthProvider: DeviceSecurityAuthProvider = DefaultDeviceSecurityAuthProvider(),
    allowDeviceCredential: Boolean = false,
): Boolean {
    val result = biometricAuthProvider.canAuthenticate(context, allowDeviceCredential)
    Timber.i("canAuthenticateUsingDeviceSecurity: $result")
    return result
}

@Composable
fun DeviceSecurityAuthHandler(
    title: String? = null,
    subtitle: String? = null,
    allowDeviceCredential: Boolean = false,
    onSuccess: () -> Unit,
    onErrorOrCancel: () -> Unit = {},
    biometricAuthProvider: DeviceSecurityAuthProvider = LocalDeviceSecurityAuthProvider.current,
) {
    biometricAuthProvider.Authenticate(
        title = title,
        subtitle = subtitle,
        allowDeviceCredential = allowDeviceCredential,
        onSuccess = onSuccess,
        onErrorOrCancel = onErrorOrCancel,
    )
}

