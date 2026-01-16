package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

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
        val visualTransformation: VisualTransformation = VisualTransformation.None,
        val maxLength: Int = Int.MAX_VALUE,
    )

    /**
     * Helper method to return formatted data to display on the UI.
     * It re-uses the visual transformations that we use TextField composable.
     *
     * We do not want password transformer because it would return masked data (big dot).
     * */
    fun getFormattedData(): String {
        var result = data
        if (cell.visualTransformation != VisualTransformation.None &&
            (cell.visualTransformation is PasswordVisualTransformation).not()
        ) {
            result = cell.visualTransformation.filter(AnnotatedString(data)).text.text
        }

        return result
    }

}
