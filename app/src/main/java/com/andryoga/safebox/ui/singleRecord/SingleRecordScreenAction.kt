package com.andryoga.safebox.ui.singleRecord

import com.andryoga.safebox.ui.singleRecord.dynamicLayout.models.FieldId

sealed interface SingleRecordScreenAction {
    data class OnCellValueUpdate(
        val fieldId: FieldId,
        val data: String,
    ) : SingleRecordScreenAction

    object OnSaveClicked : SingleRecordScreenAction

    object OnEditClicked : SingleRecordScreenAction
    object OnShareClicked : SingleRecordScreenAction
    object OnDeleteClicked : SingleRecordScreenAction
}