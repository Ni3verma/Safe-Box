package com.andryoga.safebox.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LiveData
import com.andryoga.safebox.R

@Composable
fun CommonDialog(
    isShown: LiveData<Boolean>,
    title: String,
    primaryButtonText: String = stringResource(id = R.string.close),
    onDialogDismiss: () -> Unit,
    onPrimaryButtonClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val showDialog by isShown.observeAsState(false)
    if (showDialog) {
        Dialog(onDismissRequest = { onDialogDismiss() }) {
            Surface(shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.h6,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(1f)
                    )
                    content()
                    Button(
                        onClick = { onPrimaryButtonClick() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = primaryButtonText)
                    }
                }
            }
        }
    }
}
