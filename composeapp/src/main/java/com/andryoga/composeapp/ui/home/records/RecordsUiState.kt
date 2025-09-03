package com.andryoga.composeapp.ui.home.records

data class RecordsUiState(
    val records: List<RecordListItem>? = null,
    val isLoading: Boolean = true,
    val isShowAddNewRecordsBottomSheet: Boolean = false,
)