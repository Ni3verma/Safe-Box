package com.andryoga.composeapp.data.repository

import com.andryoga.composeapp.common.AnalyticsKeys.NEW_SECURE_NOTE
import com.andryoga.composeapp.data.db.docs.SearchSecureNoteData
import com.andryoga.composeapp.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.domain.mappers.record.toDbEntity
import com.andryoga.composeapp.ui.core.models.NoteData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : SecureNoteDataRepository {
    override suspend fun upsertSecureNoteData(noteData: NoteData) {
        if (noteData.id == null || noteData.id == 0) {
            Firebase.analytics.logEvent(NEW_SECURE_NOTE, null)
        }
        secureNoteDataDaoSecure.upsertSecretNoteData(noteData.toDbEntity())
    }

    override suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDaoSecure.getAllSecretNoteData()
    }
}
//
//    override suspend fun getSecureNoteDataByKey(key: Int): SecureNoteScreenData {
//        return secureNoteDataDaoSecure.getSecretNoteDataByKey(key).toSecureNoteScreenData()
//    }
//
//    override suspend fun deleteSecureNoteDataByKey(key: Int) {
//        secureNoteDataDaoSecure.deleteSecretNoteDataByKey(key)
//    }
//
//    override suspend fun getViewSecureNoteDataByKey(key: Int): ViewSecureNoteData {
//        return secureNoteDataDaoSecure.getSecretNoteDataByKey(key).toViewSecureNoteData()
//    }
