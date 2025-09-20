package com.andryoga.composeapp.ui.home.records

import com.andryoga.composeapp.domain.models.record.RecordListItem

data class RecordsUiState(
    val records: List<RecordListItem>? = null,
    val isLoading: Boolean = true,
    val isShowAddNewRecordsBottomSheet: Boolean = false,
)