package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId

data class Layout(
    val id: LayoutId = LayoutId.UNKNOWN,
    val arrangement: List<List<Field>> = listOf(),
    val fieldUiState: Map<FieldId, FieldUiState> = emptyMap(),
) {
    data class Field(
        val fieldId: FieldId = FieldId.UNKNOWN,
        val weight: Float = 1f
    )
}