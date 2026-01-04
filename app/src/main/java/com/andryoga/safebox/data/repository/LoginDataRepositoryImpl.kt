package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.AnalyticsKeys
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.mappers.record.toLoginData
import com.andryoga.safebox.domain.models.record.LoginData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoginDataRepositoryImpl @Inject constructor(
    private val loginDataDaoSecure: LoginDataDaoSecure
) : LoginDataRepository {
    override suspend fun upsertLoginData(loginData: LoginData) {
        if (loginData.id == null || loginData.id == 0) {
            Firebase.analytics.logEvent(AnalyticsKeys.NEW_LOGIN, null)
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
}
