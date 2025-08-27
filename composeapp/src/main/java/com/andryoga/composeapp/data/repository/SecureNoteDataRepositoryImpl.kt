package com.andryoga.composeapp.data.repository

//import com.andryoga.composeapp.ui.view.home.dataDetails.secureNote.SecureNoteScreenData
//import com.andryoga.composeapp.ui.view.home.dataDetails.secureNote.SecureNoteScreenData.Companion.toSecureNoteDataEntity
//import com.andryoga.composeapp.ui.view.home.dataDetails.secureNote.SecureNoteScreenData.Companion.toSecureNoteScreenData
//import com.google.firebase.analytics.ktx.analytics
//import com.google.firebase.ktx.Firebase
import com.andryoga.composeapp.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import javax.inject.Inject

class SecureNoteDataRepositoryImpl @Inject constructor(
    private val secureNoteDataDaoSecure: SecureNoteDataDaoSecure
) : SecureNoteDataRepository {
//    override suspend fun insertSecureNoteData(secureNoteScreenData: SecureNoteScreenData) {
//        Firebase.analytics.logEvent(NEW_SECURE_NOTE, null)
//        secureNoteDataDaoSecure.insertSecretNoteData(
//            secureNoteScreenData.toSecureNoteDataEntity(
//                getCurrentDate = true
//            )
//        )
//    }
//
//    override suspend fun updateSecureNoteData(secureNoteScreenData: SecureNoteScreenData) {
//        secureNoteDataDaoSecure.updateSecretNoteData(
//            secureNoteScreenData.toSecureNoteDataEntity(
//                getCurrentDate = false
//            )
//        )
//    }
//
//    override suspend fun getAllSecureNoteData(): Flow<List<SearchSecureNoteData>> {
//        return secureNoteDataDaoSecure.getAllSecretNoteData()
//    }
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
}
