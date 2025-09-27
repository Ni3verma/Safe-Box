package com.andryoga.composeapp.ui.home.records

import com.andryoga.composeapp.domain.models.record.RecordType

data class RecordsUiState(
    val isLoading: Boolean = true,
    val isShowAddNewRecordsBottomSheet: Boolean = false,
    val searchText: String = "",
    val recordTypeFilters: List<RecordTypeFilter> = getDefaultRecordTypeFilters()
) {
    data class RecordTypeFilter(
        val recordType: RecordType,
        val isSelected: Boolean
    )
}

private fun getDefaultRecordTypeFilters(): List<RecordsUiState.RecordTypeFilter> = listOf(
    RecordsUiState.RecordTypeFilter(RecordType.LOGIN, false),
    RecordsUiState.RecordTypeFilter(RecordType.CARD, false),
    RecordsUiState.RecordTypeFilter(RecordType.BANK_ACCOUNT, false),
    RecordsUiState.RecordTypeFilter(RecordType.NOTE, false)
)
