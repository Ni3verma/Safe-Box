package com.andryoga.safebox.ui.qrScanner.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.findActivity
import timber.log.Timber

/**
 * Dialog displaying a rationale explaining why Safe-Box requires camera access
 * to scan authenticator QR codes.
 *
 * @param onAllowClick Callback invoked when user confirms permission request, with flag indicating
 * whether user is redirected to Settings (if permanently denied).
 * @param onCancelClick Callback invoked when user declines or cancels the dialog.
 * @param dismissDialogAction Action invoked to dismiss the dialog without taking further action.
 */
@Composable
fun CameraPermissionRationaleDialog(
    onAllowClick: (isRedirectingToSettings: Boolean) -> Unit,
    onCancelClick: () -> Unit,
    dismissDialogAction: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            Timber.i("Camera permission launcher result: $isGranted")
            dismissDialogAction()
        },
    )

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
                        onClick = {
                            val activity = try {
                                context.findActivity()
                            } catch (_: Exception) {
                                null
                            }

                            val shouldShowRationale = activity?.let {
                                ActivityCompat.shouldShowRequestPermissionRationale(
                                    it,
                                    Manifest.permission.CAMERA,
                                )
                            } ?: false

                            if (shouldShowRationale) {
                                Timber.i("Requesting camera permission via system dialog")
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                                onAllowClick(false)
                            } else {
                                dismissDialogAction()
                                onAllowClick(true)
                                Timber.i("Opening application details settings for camera permission")
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.allow))
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
            onAllowClick = {},
            onCancelClick = {},
            dismissDialogAction = {},
        )
    }
}
