package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.composeapp.common.Utils.isZero
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan

interface Layout {

    suspend fun getLayoutPlan(): LayoutPlan
    suspend fun saveLayout(data: Map<FieldId, String>)
    /**
     * Validate that all the mandatory fields are filled in the layout
     *
     * @return true if all mandatory fields have values, false otherwise
     */
    fun checkMandatoryFields(fieldUiState: Collection<FieldUiState>): Boolean {
        return fieldUiState.filter { it.cell.isMandatory }
            .count { it.data.isBlank() }.isZero()
    }
}