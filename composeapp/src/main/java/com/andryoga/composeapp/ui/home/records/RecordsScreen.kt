@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.composeapp.ui.home.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.domain.models.record.RecordType
import com.andryoga.composeapp.ui.core.ifNotNull
import com.andryoga.composeapp.ui.home.records.components.AddNewRecordBottomSheet
import com.andryoga.composeapp.ui.home.records.components.RecordItem
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBar
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.previewHelper.getRecordList
import com.andryoga.composeapp.ui.theme.SafeBoxTheme

@Composable
fun RecordsScreenRoot(
    setTopBar: ((@Composable () -> Unit)?) -> Unit,
    onAddNewRecord: (RecordType) -> Unit,
    onRecordClick: (id: Int, recordType: RecordType) -> Unit,
    topAppBarScrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val searchText by viewModel.searchText.collectAsState()

    LaunchedEffect(searchText) {
        setTopBar {
            RecordsSearchBar(
                query = searchText,
                onSearchTextUpdate = { viewModel.onSearchTextUpdate(it) },
                onClearSearchText = { viewModel.onClearSearchText() },
                onAddNewRecordButtonTap = {
                    viewModel.updateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = true
                    )
                },
                topAppBarScrollBehavior
            )
        }
    }

    RecordsScreen(
        uiState = uiState,
        onDismissAddNewRecordBottomSheet = {
            viewModel.updateShowAddNewRecordBottomSheet(
                showAddNewRecordBottomSheet = false
            )
        },
        onAddNewRecord = onAddNewRecord,
        onRecordClick = onRecordClick,
        topAppBarScrollBehavior = topAppBarScrollBehavior
    )
}

@Composable
private fun RecordsScreen(
    uiState: RecordsUiState,
    onDismissAddNewRecordBottomSheet: () -> Unit,
    onAddNewRecord: (RecordType) -> Unit,
    onRecordClick: (id: Int, recordType: RecordType) -> Unit,
    topAppBarScrollBehavior: TopAppBarScrollBehavior? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.isLoading.not() && uiState.records.isNullOrEmpty().not()) {
            val records = uiState.records
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .ifNotNull(
                        value = topAppBarScrollBehavior,
                        ifTrue = { Modifier.nestedScroll(it.nestedScrollConnection) }
                    ),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = records,
                    key = { it.key }
                ) { record ->
                    RecordItem(item = record, onRecordClick = onRecordClick)
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

@LightDarkModePreview
@Composable
fun RecordsScreenPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false, records = getRecordList()),
            onDismissAddNewRecordBottomSheet = {},
            onAddNewRecord = {},
            onRecordClick = { _, _ -> }
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenWithAddNewRecordBottomSheetPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                isLoading = false,
                records = getRecordList(),
                isShowAddNewRecordsBottomSheet = true
            ),
            onDismissAddNewRecordBottomSheet = {},
            onAddNewRecord = {},
            onRecordClick = { _, _ -> }
        )
    }
}
