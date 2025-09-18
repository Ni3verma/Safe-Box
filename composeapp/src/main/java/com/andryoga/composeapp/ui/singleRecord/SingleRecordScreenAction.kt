package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.ui.singleRecord.dynamicLayout.models.FieldId

sealed interface SingleRecordScreenAction {
    data class OnCellValueUpdate(
        val fieldId: FieldId,
        val data: String,
    ) : SingleRecordScreenAction

    object OnSaveClicked : SingleRecordScreenAction
}