@file:OptIn(ExperimentalMaterial3Api::class)

package com.andryoga.composeapp.ui.home.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.composeapp.domain.models.record.RecordListItem
import com.andryoga.composeapp.domain.models.record.RecordType
import com.andryoga.composeapp.ui.MainViewModel
import com.andryoga.composeapp.ui.core.ScrollBehaviorType
import com.andryoga.composeapp.ui.core.TopAppBarConfig
import com.andryoga.composeapp.ui.home.records.components.AddNewRecordBottomSheet
import com.andryoga.composeapp.ui.home.records.components.RecordItem
import com.andryoga.composeapp.ui.home.records.components.RecordTypeFilterRow
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarActions
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarNavIcon
import com.andryoga.composeapp.ui.home.records.components.RecordsSearchBarTitle
import com.andryoga.composeapp.ui.previewHelper.LightDarkModePreview
import com.andryoga.composeapp.ui.previewHelper.getRecordList
import com.andryoga.composeapp.ui.theme.SafeBoxTheme
import com.andryoga.composeapp.ui.utils.OnStart

@Composable
fun RecordsScreenRoot(
    mainViewModel: MainViewModel,
    onAddNewRecord: (RecordType) -> Unit,
    onRecordClick: (id: Int, recordType: RecordType) -> Unit,
) {
    val viewModel = hiltViewModel<RecordsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val records by viewModel.records.collectAsState()

    OnStart {
        val config = TopAppBarConfig(
            title = { RecordsSearchBarTitle(uiState.searchText, viewModel::onScreenAction) },
            navigationIcon = { RecordsSearchBarNavIcon() },
            actions = { RecordsSearchBarActions(uiState.searchText, viewModel::onScreenAction) },
            scrollBehaviorType = ScrollBehaviorType.ENTER_ALWAYS
        )
        mainViewModel.updateTopBar(config)
    }

    RecordsScreen(
        uiState = uiState,
        records = records,
        onScreenAction = { action ->
            when (action) {
                is RecordScreenAction.OnAddNewRecord -> onAddNewRecord(action.recordType)
                is RecordScreenAction.OnRecordClick -> onRecordClick(action.id, action.recordType)
                else -> viewModel.onScreenAction(action)
            }
        },
    )
}

@Composable
private fun RecordsScreen(
    uiState: RecordsUiState,
    records: List<RecordListItem>,
    onScreenAction: (RecordScreenAction) -> Unit,
) {
    if (uiState.isLoading) {
        // todo: show loading screen
    } else if (records.isEmpty()) {
        // todo: show empty view
        FilterRow(uiState, onScreenAction, Modifier.padding(horizontal = 16.dp))
    } else {
        val records = records
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { FilterRow(uiState, onScreenAction) }
            items(
                items = records,
                key = { it.key }
            ) { record ->
                RecordItem(item = record, onRecordClick = { id, recordType ->
                    onScreenAction(
                        RecordScreenAction.OnRecordClick(id, recordType)
                    )
                }
                )
            }

        }
    }

    if (uiState.isShowAddNewRecordsBottomSheet) {
        AddNewRecordBottomSheet(
            onDismiss = {
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false
                    )
                )
            },
            onAddNewRecord = {
                onScreenAction(RecordScreenAction.OnAddNewRecord(it))
                onScreenAction(
                    RecordScreenAction.OnUpdateShowAddNewRecordBottomSheet(
                        showAddNewRecordBottomSheet = false
                    )
                )
            }
        )
    }
}

@Composable
private fun FilterRow(
    uiState: RecordsUiState,
    onScreenAction: (RecordScreenAction) -> Unit,
    modifier: Modifier = Modifier
) {
    RecordTypeFilterRow(
        filters = uiState.recordTypeFilters,
        onFilterToggle = {
            onScreenAction(
                RecordScreenAction.OnToggleRecordTypeFilter(recordType = it)
            )
        },
        modifier = modifier
    )
}

@LightDarkModePreview
@Composable
private fun RecordsScreenPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false),
            records = getRecordList(),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
private fun RecordsScreenWithAddNewRecordBottomSheetPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                isLoading = false,
                isShowAddNewRecordsBottomSheet = true
            ),
            records = getRecordList(),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenNoRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = false),
            records = emptyList(),
            onScreenAction = {}
        )
    }
}

@LightDarkModePreview
@Composable
fun RecordsScreenLoadingRecordsPreview() {
    SafeBoxTheme {
        RecordsScreen(
            uiState = RecordsUiState(isLoading = true),
            records = emptyList(),
            onScreenAction = {}
        )
    }
}
