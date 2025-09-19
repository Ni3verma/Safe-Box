package com.andryoga.composeapp.domain

import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import com.andryoga.composeapp.ui.core.models.NoteData

fun NoteData.toDbEntity(): SecureNoteDataEntity {
    return SecureNoteDataEntity(
        key = id ?: 0,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}