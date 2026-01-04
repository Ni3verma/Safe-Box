package com.andryoga.safebox.ui.home.records.models

import com.andryoga.safebox.domain.models.record.RecordListItem

data class RecordsState(
    /**
     * list of records to be displayed. Some records might be filtered out based on the search query and filters
     */
    val records: List<RecordListItem> = emptyList(),

    /**
     * total number of records in the database
     */
    val totalDbRecords: Int = 0
)
