package com.andryoga.safebox.ui.core.password

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.utils.openSecuritySettings
import timber.log.Timber

/**
 * Dialog shown when the user attempts an action that requires device-level security (e.g. Master Password update/recovery)
 * but no secure lock screen (biometric, PIN, pattern, or password) is enrolled on the device.
 *
 * @param onDismiss Invoked when the user cancels or dismisses the dialog via dismiss button or back-press.
 * @param onOpenSettingsClick Invoked when the user confirms navigating to Android security settings and settings activity launches successfully.
 * @param modifier Modifier to be applied to the alert dialog.
 * @param onShow Invoked once when the dialog is displayed, typically for analytics logging.
 * @param onOpenSettingsFailure Invoked when attempting to launch Android security settings fails.
 */
@Composable
fun DeviceSecurityRequiredDialog(
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShow: () -> Unit = {},
    onOpenSettingsFailure: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onShow()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.device_security_required_title))
        },
        text = {
            Text(stringResource(R.string.device_security_required_body))
        },
        confirmButton = {
            Button(
                onClick = {
                    Timber.i("opening security settings from DeviceSecurityRequiredDialog")
                    if (context.openSecuritySettings()) {
                        onOpenSettingsClick()
                    } else {
                        onOpenSettingsFailure()
                    }
                },
            ) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        modifier = modifier,
    )
}
