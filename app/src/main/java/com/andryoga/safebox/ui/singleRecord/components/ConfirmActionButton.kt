package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun ConfirmActionButton(
    imageVector: ImageVector,
    imageContentDescription: String,
    dialogTitle: String,
    dialogText: String,
    onConfirm: () -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    ActionButton(
        onClick = { showDialog = true },
        imageVector = imageVector,
        imageContentDescription = imageContentDescription,
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = { Text(dialogTitle) },
            text = { Text(dialogText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onConfirm()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@LightDarkModePreview
@Composable
fun ConfirmActionButtonPreview() {
    SafeBoxTheme {
        ConfirmActionButton(
            imageVector = Icons.Filled.DeleteForever,
            imageContentDescription = stringResource(R.string.cd_action_delete),
            dialogTitle = stringResource(R.string.delete_this_record),
            dialogText = stringResource(R.string.delete_record_dialog_body),
            onConfirm = {}
        )
    }
}