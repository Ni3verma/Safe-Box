package com.andryoga.composeapp.ui.singleRecord

import com.andryoga.composeapp.domain.models.record.RecordType
import kotlinx.serialization.Serializable

@Serializable
data class SingleRecordScreenRoute(
    val recordType: RecordType,
    /**
     * null id means we are creating a new record.
     * Otherwise, we are viewing/editing an existing record.*/
    val id: Int? = null
)