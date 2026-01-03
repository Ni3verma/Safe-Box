package com.andryoga.composeapp.ui.home.records

import com.andryoga.composeapp.domain.models.record.RecordType

sealed interface RecordScreenAction {
    data class OnSearchTextUpdate(val searchText: String) : RecordScreenAction
    data class OnUpdateShowAddNewRecordBottomSheet(val showAddNewRecordBottomSheet: Boolean) :
        RecordScreenAction

    data class OnAddNewRecord(val recordType: RecordType) : RecordScreenAction
    data class OnRecordClick(val id: Int, val recordType: RecordType) : RecordScreenAction
    data class OnToggleRecordTypeFilter(val recordType: RecordType) : RecordScreenAction

    /**
     * User clicked allow on notification permission rationale dialog.
     * He may or may not accept it from system dialog now.
     * */
    data class OnNotificationAllowedFromRationaleDialog(
        val isRedirectingToSettingsPage: Boolean
    ) : RecordScreenAction

    /**
     * User clicked cancel on notification permission rationale dialog
     * @param neverAsk: true if "never ask again" checkbox was checked.
     * */
    data class OnCancelClickFromRationaleDialog(val neverAsk: Boolean) : RecordScreenAction
}