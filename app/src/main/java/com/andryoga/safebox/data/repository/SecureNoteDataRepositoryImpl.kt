package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.AnalyticsKeys
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.mappers.record.toNoteData
import com.andryoga.safebox.domain.models.record.NoteData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : SecureNoteDataRepository {
    override suspend fun upsertSecureNoteData(noteData: NoteData) {
        if (noteData.id == null || noteData.id == 0) {
            Firebase.analytics.logEvent(AnalyticsKeys.NEW_SECURE_NOTE, null)
        }
        secureNoteDataDaoSecure.upsertSecretNoteData(noteData.toDbEntity())
    }

    override suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDaoSecure.getAllSecretNoteData()
    }

    override suspend fun getSecureNoteDataByKey(key: Int): NoteData {
        return secureNoteDataDaoSecure.getSecretNoteDataByKey(key).toNoteData()
    }

    override suspend fun deleteSecureNoteDataByKey(key: Int) {
        secureNoteDataDaoSecure.deleteSecretNoteDataByKey(key)
    }
}
