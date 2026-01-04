package com.andryoga.safebox.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.andryoga.safebox.R
import kotlinx.serialization.Serializable

@Composable
fun UserAwayDialog(
    onExitHomeNavGraph: () -> Unit,
) {
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