package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.safebox.common.Utils.isZero
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.ShareableField

interface Layout {

    suspend fun getLayoutPlan(): LayoutPlan
    suspend fun saveLayout(data: Map<FieldId, String>)
    suspend fun deleteLayout()
    /**
     * Validate that all the mandatory fields are filled in the layout
     *
     * Fields are keyed by [FieldId] so that layouts overriding this can apply field specific
     * validation (e.g. Base32 format of an authenticator secret) without matching on labels.
     *
     * @return true if all mandatory fields have values, false otherwise
     */
    fun checkMandatoryFields(fieldUiState: Map<FieldId, FieldUiState>): Boolean {
        return fieldUiState.values.filter { it.cell.isMandatory }
            .count { it.data.isBlank() }.isZero()
    }

    /**
     * Returns the label/value pairs of this record that are safe to share as plain text.
     *
     * By default every non empty, copyable, non password field is shareable exactly as it is
     * stored. Layouts whose stored value differs from the value a user expects to share should
     * override this.
     *
     * @return ordered list of shareable label/value pairs.
     */
    suspend fun getShareableFields(): List<ShareableField> {
        return getLayoutPlan().fieldUiState
            .filter { (_, uiState) ->
                uiState.data.isEmpty().not() &&
                        uiState.cell.isCopyable &&
                        uiState.cell.isPasswordField.not()
            }
            // for the data, share formatted data because it is easier to read.
            .map { (_, uiState) -> ShareableField(uiState.cell.label, uiState.getFormattedData()) }
    }
}