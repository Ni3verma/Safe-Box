package com.andryoga.composeapp.ui.home.records.components

import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.core.models.RecordType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewRecordBottomSheet(
    onDismiss: () -> Unit,
    onAddNewRecord: (RecordType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState
    ) {
        Column {
            Text(
                text = stringResource(R.string.add_a_new_record),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            RecordType.entries.forEach {
                AddRecordItem(
                    text = stringResource(it.titleResId),
                    icon = it.icon,
                    onClick = {
                        onAddNewRecord(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun AddRecordItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = { Icon(icon, contentDescription = text) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

//todo: see why this doesnt work
@Preview(uiMode = UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun AddNewRecordBottomSheetPreview() {
    AddNewRecordBottomSheet(
        onDismiss = {},
        onAddNewRecord = {}
    )
}

@Preview
@Composable
private fun AddRecordItemPreview() {
    AddRecordItem(text = "Login", icon = Icons.Filled.VpnKey, onClick = {})
}