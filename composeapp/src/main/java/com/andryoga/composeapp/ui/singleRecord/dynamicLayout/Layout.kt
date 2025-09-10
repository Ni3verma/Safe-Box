package com.andryoga.composeapp.ui.singleRecord.dynamicLayout

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.KeyboardType

data class Layout(
    val id: LayoutId = LayoutId.UNKNOWN,
    val rows: Map<Int, List<Field>> = emptyMap()
) {
    data class Field(
        val uiState: UiState = UiState(),
        val weight: Float = 1f
    ) {
        data class UiState(
            val cell: Cell = Cell(),
            val data: String = "",
        ) {
            @Immutable
            data class Cell(
                @param:StringRes val label: Int = -1,
                val isMandatory: Boolean = false,
                val isPasswordField: Boolean = false,
                val singleLine: Boolean = true,
                val minLines: Int = 1,
                val keyboardType: KeyboardType = KeyboardType.Unspecified
            )
        }
    }
}