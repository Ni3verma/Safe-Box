package com.andryoga.safebox.data.repository

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.mappers.record.toNoteData
import com.andryoga.safebox.domain.models.record.NoteData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure,
    private val analyticsHelper: AnalyticsHelper
) : SecureNoteDataRepository {
    override suspend fun upsertSecureNoteData(noteData: NoteData) {
        if (noteData.id == null || noteData.id == 0) {
            analyticsHelper.logEvent(AnalyticsKey.NEW_SECURE_NOTE)
        }
        secureNoteDataDaoSecure.upsertSecretNoteData(noteData.toDbEntity())
    }

    override fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDaoSecure.getAllSecretNoteData()
    }

    override suspend fun getSecureNoteDataByKey(key: Int): NoteData {
        return secureNoteDataDaoSecure.getSecretNoteDataByKey(key).toNoteData()
    }

    override suspend fun deleteSecureNoteDataByKey(key: Int) {
        secureNoteDataDaoSecure.deleteSecretNoteDataByKey(key)
    }
}
