package com.andryoga.composeapp.ui.home.records

import com.andryoga.composeapp.ui.core.models.RecordType

data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val recordType: RecordType
) {
}
