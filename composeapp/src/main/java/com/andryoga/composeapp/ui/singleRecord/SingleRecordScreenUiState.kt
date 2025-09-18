package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.Layout

data class SingleRecordScreenUiState(
    val isLoading: Boolean = true,
    val isSaveEnabled: Boolean = false,
    val layout: Layout = Layout()
)