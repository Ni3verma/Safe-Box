package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.ui.core.models.LoginData


interface LoginDataRepository {
    suspend fun upsertLoginData(loginData: LoginData)
//    suspend fun getAllLoginData(): Flow<List<SearchLoginData>>
//    suspend fun getLoginDataByKey(key: Int): LoginScreenData
//    suspend fun deleteLoginDataByKey(key: Int)
//    suspend fun getViewLoginDataByKey(key: Int): ViewLoginData
}
