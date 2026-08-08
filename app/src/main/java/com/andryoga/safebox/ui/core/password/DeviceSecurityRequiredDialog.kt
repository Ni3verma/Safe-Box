package com.andryoga.safebox.ui.core.password

import android.content.Intent
import android.provider.Settings
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
import timber.log.Timber

/**
 * Dialog shown when the user attempts an action that requires device-level security (e.g. Master Password update/recovery)
 * but no secure lock screen (biometric, PIN, pattern, or password) is enrolled on the device.
 *
 * @param onDismiss Invoked when the user cancels or dismisses the dialog via dismiss button or back-press.
 * @param onOpenSettingsClick Invoked when the user confirms navigating to Android security settings.
 * @param modifier Modifier to be applied to the alert dialog.
 * @param onShow Invoked once when the dialog is displayed, typically for analytics logging.
 */
@Composable
fun DeviceSecurityRequiredDialog(
    onDismiss: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShow: () -> Unit = {},
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
                    onOpenSettingsClick()
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }.onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
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
