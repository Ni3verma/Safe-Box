package com.andryoga.composeapp.data.repository

import com.andryoga.composeapp.common.AnalyticsKeys.NEW_LOGIN
import com.andryoga.composeapp.data.db.docs.SearchLoginData
import com.andryoga.composeapp.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.domain.mappers.record.toDbEntity
import com.andryoga.composeapp.domain.mappers.record.toLoginData
import com.andryoga.composeapp.domain.models.record.LoginData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginDataRepositoryImpl @Inject constructor(
    private val loginDataDaoSecure: LoginDataDaoSecure
) : LoginDataRepository {
    override suspend fun upsertLoginData(loginData: LoginData) {
        if (loginData.id == null || loginData.id == 0) {
            Firebase.analytics.logEvent(NEW_LOGIN, null)
        }
        loginDataDaoSecure.upsertLoginData(loginData.toDbEntity())
    }

    override suspend fun getAllLoginData(): Flow<List<SearchLoginData>> {
        return loginDataDaoSecure.getAllLoginData()
    }

    override suspend fun getLoginDataByKey(key: Int): LoginData {
        return loginDataDaoSecure.getLoginDataByKey(key).toLoginData()
    }

    override suspend fun deleteLoginDataByKey(key: Int) {
        loginDataDaoSecure.deleteLoginDataByKey(key)
    }
//
//    override suspend fun getViewLoginDataByKey(key: Int): ViewLoginData {
//        return loginDataDaoSecure.getLoginDataByKey(key).toViewLoginData()
//    }
}
