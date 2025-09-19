package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.ui.core.models.NoteData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date
import javax.inject.Inject

class NoteLayoutImpl @Inject constructor(
    private val secureNoteDataRepositoryImpl: SecureNoteDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override fun getLayoutPlan(): LayoutPlan {
        return layoutPlan ?: getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        secureNoteDataRepositoryImpl.upsertSecureNoteData(
            NoteData(
                id = null,
                title = data[FieldId.NOTE_TITLE] ?: "",
                notes = data[FieldId.NOTE_NOTES] ?: "",
                creationDate = Date(),
            )
        )
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
                        label = R.string.notes, singleLine = true, minLines = 5
                    )
                )
            )
        )
        layoutPlan = plan
        return plan
    }
}