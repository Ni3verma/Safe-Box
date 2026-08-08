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

@Composable
fun DeviceSecurityRequiredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onShow: () -> Unit = {},
    onOpenSettingsClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        onShow()
    }
    AlertDialog(
        onDismissRequest = onCancelClick,
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
                    onDismiss()
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    }.onFailure {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelClick) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        modifier = modifier,
    )
}
