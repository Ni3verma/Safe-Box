package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.input.KeyboardType

data class FieldUiState(
    val cell: Cell = Cell(),
    val data: String = "",
) {
    /*
     * This holds the static properties of the cell.
     * */
    @Immutable
    data class Cell(
        @param:StringRes val label: Int = -1,
        val isMandatory: Boolean = false,
        val isPasswordField: Boolean = false,
        val singleLine: Boolean = true,
        val minLines: Int = 1,
        val keyboardType: KeyboardType = KeyboardType.Unspecified,

        // If set to true, this cell will be visible only in view mode. e.g creation date
        val isVisibleOnlyInViewMode: Boolean = false,
        val isCopyable: Boolean = false,
    )
}
