package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.domain.models.record.NoteData
import kotlinx.coroutines.flow.Flow

interface SecureNoteDataRepository {
    suspend fun upsertSecureNoteData(noteData: NoteData)
    suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>>
    suspend fun getSecureNoteDataByKey(key: Int): NoteData
    suspend fun deleteSecureNoteDataByKey(key: Int)
}
