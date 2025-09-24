package com.andryoga.composeapp.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.composeapp.R
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.domain.models.record.NoteData
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date

class NoteLayoutImpl(
    private val recordId: Int?,
    private val secureNoteDataRepositoryImpl: SecureNoteDataRepository
) : Layout {
    private var layoutPlan: LayoutPlan? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        val recordData =
            recordId?.let { secureNoteDataRepositoryImpl.getSecureNoteDataByKey(recordId) }
        return layoutPlan ?: getLayoutPlanInternal(recordData)
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        secureNoteDataRepositoryImpl.upsertSecureNoteData(
            NoteData(
                id = null,
                title = data[FieldId.NOTE_TITLE] ?: "",
                notes = data[FieldId.NOTE_NOTES] ?: "",
                creationDate = Date(),
                updateDate = Date(),
            )
        )
    }

    private fun getLayoutPlanInternal(recordData: NoteData?): LayoutPlan {
        val plan = LayoutPlan(
            id = LayoutId.NOTE,
            arrangement = listOf(
                listOf(LayoutPlan.Field(fieldId = FieldId.NOTE_TITLE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.NOTE_NOTES)),
                listOf(LayoutPlan.Field(fieldId = FieldId.CREATION_DATE)),
                listOf(LayoutPlan.Field(fieldId = FieldId.UPDATE_DATE))
            ),
            fieldUiState = mapOf(
                FieldId.NOTE_TITLE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.title, isMandatory = true
                    ),
                    data = recordData?.title.orEmpty()
                ),
                FieldId.NOTE_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes, isMandatory = true, singleLine = true, minLines = 5
                    ),
                    data = recordData?.notes.orEmpty()
                ),
                FieldId.CREATION_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.created_on, isVisibleOnlyInViewMode = true
                    ),
                    data = recordData?.creationDate?.toString().orEmpty()
                ),
                FieldId.UPDATE_DATE to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.updated_on, isVisibleOnlyInViewMode = true
                    ),
                    data = recordData?.updateDate?.toString().orEmpty()
                ),
            )
        )
        layoutPlan = plan
        return plan
    }
}