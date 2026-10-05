package com.andryoga.safebox.ui.home.records.models

import com.andryoga.safebox.domain.models.record.RecordType

/**
 * Driving state for the records list that is not text input. The search query is deliberately
 * not here: it lives in `RecordsViewModel.searchTextState` as a `TextFieldState`, because a text
 * field must see its own edits synchronously and a value routed through a flow does not.
 */
data class UserInputs(
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
    UserInputs.RecordTypeFilter(RecordType.NOTE, false),
    UserInputs.RecordTypeFilter(RecordType.AUTHENTICATOR, false),
)
