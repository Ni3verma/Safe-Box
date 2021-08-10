package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.ui.view.home.addNewData.secureNote.SecureNoteScreenData
import java.util.*
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDao: SecureNoteDataDao
) : SecureNoteDataRepository {
    override suspend fun insertSecureNoteData(secureNoteScreenData: SecureNoteScreenData) {
        val entity = SecureNoteDataEntity(
            secureNoteScreenData.title,
            secureNoteScreenData.notes,
            Date(),
            Date()
        )
        secureNoteDataDao.insertSecretNoteData(entity)
    }
}
