package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.data.db.docs.SearchSecureNoteData
import com.andryoga.composeapp.ui.core.models.NoteData
import kotlinx.coroutines.flow.Flow

interface SecureNoteDataRepository {
    suspend fun upsertSecureNoteData(noteData: NoteData)
    suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>>
//    suspend fun getSecureNoteDataByKey(key: Int): SecureNoteScreenData
//    suspend fun deleteSecureNoteDataByKey(key: Int)
//    suspend fun getViewSecureNoteDataByKey(key: Int): ViewSecureNoteData
}
