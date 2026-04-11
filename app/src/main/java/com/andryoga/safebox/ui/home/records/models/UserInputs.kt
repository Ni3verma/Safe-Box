package com.andryoga.safebox.ui.home.records.models

import com.andryoga.safebox.domain.models.record.RecordType

data class UserInputs(
    val searchText: String = "",
    val recordTypeFilters: List<RecordTypeFilter> = getDefaultRecordTypeFilters(),
    val isAddNewRecordBottomSheetVisible: Boolean = false,
) {
    data class RecordTypeFilter(
        val recordType: RecordType,
        val isSelected: Boolean
    )
}

fun getDefaultRecordTypeFilters(): List<UserInputs.RecordTypeFilter> = listOf(
    UserInputs.RecordTypeFilter(RecordType.LOGIN, false),
    UserInputs.RecordTypeFilter(RecordType.CARD, false),
    UserInputs.RecordTypeFilter(RecordType.BANK_ACCOUNT, false),
    UserInputs.RecordTypeFilter(RecordType.NOTE, false)
)
