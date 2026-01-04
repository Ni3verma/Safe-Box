package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.singleRecord.SingleRecordScreenAction
import com.andryoga.safebox.ui.theme.SafeBoxTheme

@Composable
fun ActionButtonRow(
    screenAction: (SingleRecordScreenAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            onClick = { screenAction(SingleRecordScreenAction.OnEditClicked) },
            imageVector = Icons.Filled.Edit,
            imageContentDescription = stringResource(R.string.cd_action_edit),
        )

        ActionButton(
            onClick = { screenAction(SingleRecordScreenAction.OnShareClicked) },
            imageVector = Icons.Filled.Share,
            imageContentDescription = stringResource(R.string.cd_action_share),
        )

        ConfirmActionButton(
            imageVector = Icons.Filled.DeleteForever,
            imageContentDescription = stringResource(R.string.cd_action_delete),
            dialogTitle = stringResource(R.string.delete_this_record),
            dialogText = stringResource(R.string.delete_record_dialog_body),
            onConfirm = { screenAction(SingleRecordScreenAction.OnDeleteClicked) },
        )
    }
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    imageContentDescription: String
) {
    Button(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = imageContentDescription,
            modifier = Modifier
                .size(32.dp)
        )
    }
}

@LightDarkModePreview
@Composable
private fun ActionButtonRowPreview() {
    SafeBoxTheme { ActionButtonRow(screenAction = {}) }
}

@LightDarkModePreview
@Composable
private fun ActionButtonPreview() {
    SafeBoxTheme {
        ActionButton(
            onClick = {},
            imageVector = Icons.Filled.DeleteForever,
            imageContentDescription = "Delete"
        )
    }
}