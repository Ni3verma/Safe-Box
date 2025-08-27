package com.andryoga.composeapp.data.repository

//import com.andryoga.composeapp.ui.view.home.dataDetails.login.LoginScreenData
//import com.andryoga.composeapp.ui.view.home.dataDetails.login.LoginScreenData.Companion.toLoginDataEntity
//import com.andryoga.composeapp.ui.view.home.dataDetails.login.LoginScreenData.Companion.toLoginScreenData
//import com.google.firebase.analytics.ktx.analytics
//import com.google.firebase.ktx.Firebase
import com.andryoga.composeapp.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
class LoginDataRepositoryImpl @Inject constructor(
    private val loginDataDaoSecure: LoginDataDaoSecure
) : LoginDataRepository {
//    override suspend fun insertLoginData(loginScreenData: LoginScreenData) {
//        Firebase.analytics.logEvent(NEW_LOGIN, null)
//        loginDataDaoSecure.insertLoginData(loginScreenData.toLoginDataEntity(getCurrentDate = true))
//    }
//
//    override suspend fun updateLoginData(loginScreenData: LoginScreenData) {
//        loginDataDaoSecure.updateLoginData(loginScreenData.toLoginDataEntity(getCurrentDate = false))
//    }
//
//    override suspend fun getAllLoginData(): Flow<List<SearchLoginData>> {
//        return loginDataDaoSecure.getAllLoginData()
//    }
//
//    override suspend fun getLoginDataByKey(key: Int): LoginScreenData {
//        return loginDataDaoSecure.getLoginDataByKey(key).toLoginScreenData()
//    }
//
//    override suspend fun deleteLoginDataByKey(key: Int) {
//        loginDataDaoSecure.deleteLoginDataByKey(key)
//    }
//
//    override suspend fun getViewLoginDataByKey(key: Int): ViewLoginData {
//        return loginDataDaoSecure.getLoginDataByKey(key).toViewLoginData()
//    }
}
