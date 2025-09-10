package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.Layout

data class SingleRecordScreenUiState(
    val isLoading: Boolean = true,
    val layout: Layout = Layout()
)