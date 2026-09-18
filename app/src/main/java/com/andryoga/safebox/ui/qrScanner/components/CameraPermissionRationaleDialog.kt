package com.andryoga.safebox.ui.qrScanner.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Dialog displaying a rationale explaining why Safe-Box requires camera access
 * to scan authenticator QR codes.
 *
 * @param isPermanentlyDenied Whether a request was already denied by the system without showing a
 * prompt. When true the confirm button offers app settings, because re-requesting is a no-op.
 * @param onAllowClick Callback invoked when the user asks to be prompted for the permission.
 * @param onOpenSettingsClick Callback invoked when the user chooses to fix the permission from
 * the system app settings page.
 * @param onCancelClick Callback invoked when user declines or cancels the dialog.
 * @param dismissDialogAction Action invoked to dismiss the dialog without taking further action.
 */
// https://github.com/Ni3verma/Safe-Box/issues/239
// TODO: Extract common PermissionRationaleDialog base component to share layout between CameraPermissionRationaleDialog and NotificationPermissionRationaleDialog
@Composable
fun CameraPermissionRationaleDialog(
    isPermanentlyDenied: Boolean,
    onAllowClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    onCancelClick: () -> Unit,
    dismissDialogAction: () -> Unit,
) {
    Dialog(
        onDismissRequest = { dismissDialogAction() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.camera_permission_rationale_dialog_heading),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.camera_permission_rationale_dialog_body),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = if (isPermanentlyDenied) onOpenSettingsClick else onAllowClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            stringResource(
                                if (isPermanentlyDenied) {
                                    R.string.open_settings
                                } else {
                                    R.string.allow
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@LightDarkModePreview
@Composable
private fun CameraPermissionRationaleDialogPreview() {
    SafeBoxTheme {
        CameraPermissionRationaleDialog(
            isPermanentlyDenied = false,
            onAllowClick = {},
            onOpenSettingsClick = {},
            onCancelClick = {},
            dismissDialogAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun CameraPermissionRationaleDialogPermanentlyDeniedPreview() {
    SafeBoxTheme {
        CameraPermissionRationaleDialog(
            isPermanentlyDenied = true,
            onAllowClick = {},
            onOpenSettingsClick = {},
            onCancelClick = {},
            dismissDialogAction = {},
        )
    }
}
