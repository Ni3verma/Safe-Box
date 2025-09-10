package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.ui.core.models.RecordType
import kotlinx.serialization.Serializable

@Serializable
data class SingleRecordScreenRoute(
    val recordType: RecordType
)