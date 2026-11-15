package com.andryoga.safebox.ui.login.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R

/**
 * Confirmation for *Erase everything and start over*: the vault, its master password
 * and its recovery keys on this device are deleted and the app returns to signup. Backup files on
 * external storage are not touched, which the copy says so a user with one knows the way back.
 *
 * @param onDismiss Invoked when the user cancels or dismisses the dialog.
 * @param onConfirm Invoked when the user confirms the erase.
 * @param modifier Modifier to be applied to the alert dialog.
 * @param onShow Invoked once when the dialog is displayed, for analytics logging.
 */
@Composable
fun EraseVaultDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onShow: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        onShow()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.erase_vault_dialog_title))
        },
        text = {
            Text(stringResource(R.string.erase_vault_dialog_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                    contentColor = colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.erase_vault_dialog_confirm))
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
