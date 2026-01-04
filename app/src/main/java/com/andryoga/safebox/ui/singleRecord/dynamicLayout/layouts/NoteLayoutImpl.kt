package com.andryoga.safebox.ui.singleRecord.dynamicLayout.layouts

import com.andryoga.safebox.R
import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.NoteData
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.LayoutId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldUiState
import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.LayoutPlan
import java.util.Date

class NoteLayoutImpl(
    private val recordId: Int?,
    private val secureNoteDataRepositoryImpl: SecureNoteDataRepository
) : Layout {
    private var recordData: NoteData? = null

    override suspend fun getLayoutPlan(): LayoutPlan {
        recordData =
            recordId?.let { secureNoteDataRepositoryImpl.getSecureNoteDataByKey(recordId) }
        return getLayoutPlanInternal()
    }

    override suspend fun saveLayout(data: Map<FieldId, String>) {
        secureNoteDataRepositoryImpl.upsertSecureNoteData(
            NoteData(
                id = recordId,
                title = data[FieldId.NOTE_TITLE] ?: "",
                notes = data[FieldId.NOTE_NOTES] ?: "",
                creationDate = recordData?.creationDate ?: Date(),
                updateDate = Date(),
            )
        )
    }

    override suspend fun deleteLayout() {
        if (recordId != null) {
            secureNoteDataRepositoryImpl.deleteSecureNoteDataByKey(recordId)
        } else {
            Utils.crashInDebugBuild("recordId is null, cannot delete")
        }
    }

    private fun getLayoutPlanInternal(): LayoutPlan {
        return LayoutPlan(
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
                        label = R.string.title, isMandatory = true, isCopyable = true,
                    ),
                    data = recordData?.title.orEmpty()
                ),
                FieldId.NOTE_NOTES to FieldUiState(
                    cell = FieldUiState.Cell(
                        label = R.string.notes,
                        isMandatory = true,
                        singleLine = false,
                        minLines = 5,
                        isCopyable = true,
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
    }
}