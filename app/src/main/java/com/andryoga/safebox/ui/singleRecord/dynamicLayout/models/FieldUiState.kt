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
     * This holds the properties of the cell that are fixed once the layout plan is built.
     * Unlike [data], nothing here is ever updated by user input. A property may still be derived
     * from the record being shown, e.g. the TOTP generation parameters carried by [FieldType.Totp].
     * */
    @Immutable
    data class Cell(
        @param:StringRes val label: Int = -1,
        val isMandatory: Boolean = false,
        val isPasswordField: Boolean = false,
        val singleLine: Boolean = true,
        val minLines: Int = 1,
        val maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
        val keyboardType: KeyboardType = KeyboardType.Unspecified,

        // Modes this cell renders in. Defaults to every mode; narrow it to restrict a cell, e.g.
        // a creation date is setOf(VIEW) and an authenticator seed is setOf(NEW).
        val visibleIn: Set<ViewMode> = ViewMode.entries.toSet(),
        val isCopyable: Boolean = false,
        val visualTransformation: VisualTransformation = VisualTransformation.None,
        val maxLength: Int = Int.MAX_VALUE,
        val type: FieldType = FieldType.DefaultText,
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

    /**
     * Decides whether this field renders in [viewMode].
     *
     * View mode additionally hides blank fields, so a record saved without an optional value does
     * not show a stranded label. Create and edit modes render any allowed cell regardless, because
     * the user needs the empty input to type into.
     *
     * @param viewMode Mode the screen is currently showing.
     * @return true when the field should be laid out.
     */
    fun isVisibleIn(viewMode: ViewMode): Boolean =
        viewMode in cell.visibleIn && (viewMode != ViewMode.VIEW || data.isNotBlank())
}
