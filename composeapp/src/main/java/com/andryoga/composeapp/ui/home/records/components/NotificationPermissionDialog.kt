package com.andryoga.composeapp.ui.home.records.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.home.records.models.NotificationPermissionState
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import com.andryoga.composeapp.ui.utils.findActivity
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NotificationPermissionRationaleDialog(
    isNotificationPermissionAskedBefore: Boolean,
    onAllowClick: (isRedirectingToSettings: Boolean) -> Unit,
    onCancelClick: (Boolean) -> Unit,
    dismissDialogAction: () -> Unit,
) {
    var doNotAskAgain by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            /** we do not care about the result*/
            Timber.i("got result of permission launcher")
            dismissDialogAction()
        }
    )
    val context = LocalContext.current

    Dialog(
        onDismissRequest = { /*we do not allow dialog to be dismissed*/ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.notification_permission_rationale_dialog_heading),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.notification_permission_rationale_dialog_body),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { doNotAskAgain = !doNotAskAgain }
                ) {
                    Checkbox(
                        checked = doNotAskAgain,
                        onCheckedChange = { isChecked -> doNotAskAgain = isChecked },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = stringResource(R.string.do_not_ask_again),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onCancelClick(doNotAskAgain)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            if (isNotificationPermissionAskedBefore.not()) {
                                Timber.i("asking notification permission for the first time")
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                onAllowClick(false)
                            } else {
                                if (ActivityCompat.shouldShowRequestPermissionRationale(
                                        context.findActivity(),
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                ) {
                                    Timber.i("asking notification permission")
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    onAllowClick(false)
                                } else {
                                    // user has permanently denied permission, need to give from settings page
                                    dismissDialogAction()
                                    onAllowClick(true)
                                    Timber.i("opening settings page for notification permission")
                                    val intent =
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(
                                                Settings.EXTRA_APP_PACKAGE,
                                                context.packageName
                                            )
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                }
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


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun shouldShowNotificationPermissionRationaleDialog(
    showNotificationPermissionRationaleDialog: Boolean,
    notificationPermissionState: NotificationPermissionState,
    context: Context
): Boolean {
    return showNotificationPermissionRationaleDialog &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED &&
            notificationPermissionState.isNeverAskForNotificationPermission.not() &&
            notificationPermissionState.isBackupPathSet
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@LightDarkModePreview
@Composable
private fun NotificationPermissionDialogPreview() {
    SafeBoxTheme {
        NotificationPermissionRationaleDialog(
            isNotificationPermissionAskedBefore = true,
            onAllowClick = {},
            onCancelClick = {},
            dismissDialogAction = {}
        )
    }
}