package com.andryoga.composeapp.ui.record

import com.andryoga.composeapp.ui.record.dynamicLayout.Layout

data class SingleRecordScreenUiState(
    val isLoading: Boolean = true,
    val layout: Layout = Layout()
)