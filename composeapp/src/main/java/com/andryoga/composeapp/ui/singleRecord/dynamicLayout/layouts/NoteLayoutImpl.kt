package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.composeapp.R
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan

class NoteLayoutImpl : Layout {
    private var layoutPlan: LayoutPlan? = null

    override fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        val plan = LayoutPlan(
            id = LayoutId.NOTE,
            arrangement = listOf(
                listOf(LayoutPlan.Field(fieldId = FieldId.NOTE_TITLE)),
                listOf(
                    LayoutPlan.Field(fieldId = FieldId.NOTE_NOTES)
                )
            ),
            fieldUiState = mapOf(
                FieldId.NOTE_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    )
                ),
                FieldId.NOTE_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, singleLine = false, minLines = 5
                    )
                )
            )
        )
        layoutPlan = plan
        return plan
    }
}