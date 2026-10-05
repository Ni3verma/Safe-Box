package com.andryoga.safebox.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andryoga.safebox.R
import kotlinx.serialization.Serializable

/**
 * Blocking dialog shown when the session timed out while the user was away; the only way out is
 * the confirm button, which hands control to [onExitHomeNavGraph].
 *
 * The dialog is its own window, so left alone it would stay up - scrim included - for the whole
 * root navigation transition and dim the login screen's entrance. It is therefore dropped the
 * moment the user confirms and the screen underneath fades out on its own.
 */
@Composable
fun UserAwayDialog(
    onExitHomeNavGraph: () -> Unit,
) {
    var isConfirmed by remember { mutableStateOf(false) }
    if (isConfirmed) return
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
            ) {
                Text(text = stringResource(R.string.timeout_dialog_message))
                Button(onClick = {
                    isConfirmed = true
                    onExitHomeNavGraph()
                }, modifier = Modifier.align(Alignment.End)) {
                    Text(text = stringResource(R.string.timeout_dialog_positive_button_text))
                }
            }
        }
    }
}

@Serializable
object UserAwayDialogRoute