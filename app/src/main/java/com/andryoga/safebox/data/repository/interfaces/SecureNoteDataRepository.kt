package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.docs.ViewSecureNoteData
import com.andryoga.safebox.ui.view.home.dataDetails.secureNote.SecureNoteScreenData
import kotlinx.coroutines.flow.Flow

interface SecureNoteDataRepository {
    suspend fun insertSecureNoteData(secureNoteScreenData: SecureNoteScreenData)
    suspend fun updateSecureNoteData(secureNoteScreenData: SecureNoteScreenData)
    suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>>
    suspend fun getSecureNoteDataByKey(key: Int): SecureNoteScreenData
    suspend fun deleteSecureNoteDataByKey(key: Int)
    suspend fun getViewSecureNoteDataByKey(key: Int): ViewSecureNoteData
}
