package com.andryoga.safebox.ui.singleRecord

import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ViewMode

data class SingleRecordScreenUiState(
    val isLoading: Boolean = true,
    val topAppBarUiState: TopAppBarUiState = TopAppBarUiState(),
    val layoutPlan: LayoutPlan = LayoutPlan(),
    val viewMode: ViewMode = ViewMode.NEW
) {
    data class TopAppBarUiState(
        val title: String = "",
        val isSaveButtonEnabled: Boolean = false,
        val isSaveButtonVisible: Boolean = false
    )
}
