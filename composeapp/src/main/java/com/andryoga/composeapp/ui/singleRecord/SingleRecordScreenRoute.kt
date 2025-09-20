package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.domain.models.record.RecordType
import kotlinx.serialization.Serializable

@Serializable
data class SingleRecordScreenRoute(
    val recordType: RecordType
)