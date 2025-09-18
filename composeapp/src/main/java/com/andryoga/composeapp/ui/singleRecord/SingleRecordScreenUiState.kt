package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan

data class SingleRecordScreenUiState(
    val isLoading: Boolean = true,
    /**
     * whether save button on the top app bar is enabled or not
     * */
    val isSaveEnabled: Boolean = false,
    val layoutPlan: LayoutPlan = LayoutPlan()
)
