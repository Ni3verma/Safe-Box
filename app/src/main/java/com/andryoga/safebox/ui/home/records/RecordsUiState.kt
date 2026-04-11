package com.andryoga.safebox.ui.home.records

import com.andryoga.safebox.domain.models.record.RecordListItem
import com.andryoga.safebox.ui.home.records.models.UserInputs
import com.andryoga.safebox.ui.home.records.models.getDefaultRecordTypeFilters

data class RecordsUiState(
    // show loader on the screen, this is a derived property
    val isLoading: Boolean = true,

    // when true, add new record BS is visible on the screen
    val isShowAddNewRecordsBottomSheet: Boolean = false,

    // the search text that user has entered
    val searchText: String = "",

    // all the possible record type filters
    val recordTypeFilters: List<UserInputs.RecordTypeFilter> = getDefaultRecordTypeFilters(),
    /**
     * list of records to be displayed. Some records might be filtered out based on the search query and filters
     */
    val records: List<RecordListItem> = emptyList(),

    /**
     * total number of records in the database
     */
    val totalDbRecords: Int = 0
)

