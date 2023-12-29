package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.ViewLoginData
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData
import kotlinx.coroutines.flow.Flow

interface LoginDataRepository {
    suspend fun insertLoginData(loginScreenData: LoginScreenData)

    suspend fun updateLoginData(loginScreenData: LoginScreenData)

    suspend fun getAllLoginData(): Flow<List<SearchLoginData>>

    suspend fun getLoginDataByKey(key: Int): LoginScreenData

    suspend fun deleteLoginDataByKey(key: Int)

    suspend fun getViewLoginDataByKey(key: Int): ViewLoginData
}
