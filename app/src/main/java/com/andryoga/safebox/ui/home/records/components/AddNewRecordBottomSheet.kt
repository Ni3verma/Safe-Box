package com.andryoga.safebox.ui.home.records.components

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
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.domain.models.record.RecordType
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.getIcon
import com.andryoga.safebox.ui.utils.getTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewRecordBottomSheet(
    onDismiss: () -> Unit,
    onAddNewRecord: (RecordType) -> Unit
) {
    val sheetState = rememberStandardBottomSheetState(
        skipHiddenState = false
    )

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
                    text = it.getTitle(),
                    icon = it.getIcon(),
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

@LightDarkModePreview
@Composable
private fun AddNewRecordBottomSheetPreview() {
    SafeBoxTheme {
        AddNewRecordBottomSheet(
            onDismiss = {},
            onAddNewRecord = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun AddRecordItemPreview() {
    SafeBoxTheme {
        AddRecordItem(text = "Login", icon = Icons.Filled.VpnKey, onClick = {})
    }
}
