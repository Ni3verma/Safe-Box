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
    object OnCopyTotpCode : SingleRecordScreenAction

    /**
     * User copied a field's value in view mode. The copy has already happened in the UI; this
     * only reports it. Authenticator codes go through [OnCopyTotpCode] instead.
     */
    data class OnFieldCopied(val fieldId: FieldId) : SingleRecordScreenAction
}