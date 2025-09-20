package com.andryoga.composeapp.domain.models.record

data class RecordListItem(
    val id: Int,
    val title: String,
    val subTitle: String?,
    val recordType: RecordType,
    val key: String = "${recordType.name}_$id"
)