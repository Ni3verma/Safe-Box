package com.andryoga.composeapp.ui.singleRecord.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.singleRecord.SingleRecordScreenAction
import com.andryoga.composeapp.ui.theme.SafeBoxTheme

@Composable
fun ActionButtonRow(
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FloatingActionButton(onClick = { screenAction(SingleRecordScreenAction.OnEditClicked) }) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = stringResource(R.string.cd_action_edit),
            )
        }

        FloatingActionButton(onClick = { screenAction(SingleRecordScreenAction.OnShareClicked) }) {
            Icon(
                Icons.Filled.Share,
                contentDescription = stringResource(R.string.cd_action_share),
            )
        }
        FloatingActionButton(onClick = { screenAction(SingleRecordScreenAction.OnDeleteClicked) }) {
            Icon(
                Icons.Filled.DeleteForever,
                contentDescription = stringResource(R.string.cd_action_delete),
            )
        }
    }
}

@LightDarkModePreview
@Composable
private fun ActionButtonRowPreview() {
    SafeBoxTheme { ActionButtonRow(screenAction = {}) }
}