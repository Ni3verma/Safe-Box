package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.data.db.docs.SearchLoginData
import com.andryoga.composeapp.domain.models.record.LoginData
import kotlinx.coroutines.flow.Flow


interface LoginDataRepository {
    suspend fun upsertLoginData(loginData: LoginData)
    suspend fun getAllLoginData(): Flow<List<SearchLoginData>>
    suspend fun getLoginDataByKey(key: Int): LoginData
    suspend fun deleteLoginDataByKey(key: Int)
}
