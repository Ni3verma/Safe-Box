package com.andryoga.composeapp.ui.home.records

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.andryoga.composeapp.ui.home.records.components.AddNewRecordBottomSheet
import com.andryoga.composeapp.ui.home.records.components.RecordItem
import com.andryoga.composeapp.ui.previewHelper.getRecordList

@Composable
fun RecordsScreenRoot(
    showAddNewRecordBottomSheet: Boolean,
    onDismissAddNewRecordBottomSheet: () -> Unit,
    onAddNewRecord: (RecordListItem.Type) -> Unit,
) {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(showAddNewRecordBottomSheet) {
        viewModel.updateShowAddNewRecordBottomSheet(showAddNewRecordBottomSheet)
    }

    RecordsScreen(
        uiState = uiState,
        onDismissAddNewRecordBottomSheet = onDismissAddNewRecordBottomSheet,
        onAddNewRecord = onAddNewRecord
    )
}

@Composable
private fun RecordsScreen(
    uiState: RecordsUiState,
    onDismissAddNewRecordBottomSheet: () -> Unit,
    onAddNewRecord: (RecordListItem.Type) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.isLoading.not() && uiState.records.isNullOrEmpty().not()) {
            val records = uiState.records
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = records, key = { it.id }) { record ->
                    RecordItem(item = record)
                }
            }
        }
    }

    if (uiState.isShowAddNewRecordsBottomSheet) {
        AddNewRecordBottomSheet(
            onDismiss = { onDismissAddNewRecordBottomSheet() },
            onAddNewRecord = {
                onAddNewRecord(it)
                onDismissAddNewRecordBottomSheet()
            }
        )
    }
}


@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordsScreenPreview() {
    RecordsScreen(
        uiState = RecordsUiState(isLoading = false, records = getRecordList()),
        onDismissAddNewRecordBottomSheet = {},
        onAddNewRecord = {}
    )
}

//todo: see why this doesnt work
@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun RecordsScreenWithAddNewRecordBottomSheetPreview() {
    RecordsScreen(
        uiState = RecordsUiState(
            isLoading = false,
            records = getRecordList(),
            isShowAddNewRecordsBottomSheet = true
        ),
        onDismissAddNewRecordBottomSheet = {},
        onAddNewRecord = {}
    )
}