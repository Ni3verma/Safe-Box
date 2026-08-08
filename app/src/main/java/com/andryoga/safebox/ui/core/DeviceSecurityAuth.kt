package com.andryoga.safebox.ui.core

import android.content.Context
import androidx.compose.runtime.Composable
import timber.log.Timber

/**
 * Helper function to query device authentication capability using the provided [DeviceSecurityAuthProvider].
 *
 * @param context Application/Activity context.
 * @param biometricAuthProvider Provider implementation to delegate capability check to.
 * @param allowDeviceCredential Whether to include device credentials (PIN/Pattern/Password) in check.
 */
fun canAuthenticateUsingDeviceSecurity(
    context: Context,
    biometricAuthProvider: DeviceSecurityAuthProvider = DefaultDeviceSecurityAuthProvider(),
    allowDeviceCredential: Boolean = false,
): Boolean {
    val result = biometricAuthProvider.canAuthenticate(context, allowDeviceCredential)
    Timber.i("canAuthenticateUsingDeviceSecurity: $result")
    return result
}

/**
 * Composable wrapper triggering biometric or device credential authentication via [DeviceSecurityAuthProvider].
 */
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

