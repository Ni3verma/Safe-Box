package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.secureNote.SecureNoteScreenData
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : SecureNoteDataRepository {
    override suspend fun insertSecureNoteData(secureNoteScreenData: SecureNoteScreenData) {
        val entity = SecureNoteDataEntity(
            secureNoteScreenData.title,
            secureNoteScreenData.notes,
            Date(),
            Date()
        )
        secureNoteDataDaoSecure.insertSecretNoteData(entity)
    }

    override suspend fun updateSecureNoteData(secureNoteScreenData: SecureNoteScreenData) {
        TODO("Not yet implemented")
    }

    override suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>> {
        return secureNoteDataDaoSecure.getAllSecretNoteData()
    }
}
