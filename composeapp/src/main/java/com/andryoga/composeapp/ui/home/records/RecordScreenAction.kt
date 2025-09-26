package com.andryoga.composeapp.ui.home.records

import com.andryoga.composeapp.domain.models.record.RecordType

sealed interface RecordScreenAction {
    data class OnSearchTextUpdate(val searchText: String) : RecordScreenAction
    data class OnUpdateShowAddNewRecordBottomSheet(val showAddNewRecordBottomSheet: Boolean) :
        RecordScreenAction

    data class OnAddNewRecord(val recordType: RecordType) : RecordScreenAction
    data class OnRecordClick(val id: Int, val recordType: RecordType) : RecordScreenAction
    data class OnToggleRecordTypeFilter(val recordType: RecordType) : RecordScreenAction
}