package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData.Companion.toAddNewLoginScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData.Companion.toLoginDataEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ExperimentalCoroutinesApi
class LoginDataRepositoryImpl @Inject constructor(
    private val loginDataDaoSecure: LoginDataDaoSecure
) : LoginDataRepository {
    override suspend fun insertLoginData(loginScreenData: LoginScreenData) {
        loginDataDaoSecure.insertLoginData(loginScreenData.toLoginDataEntity())
    }

    override suspend fun getAllLoginData(): Flow<List<SearchLoginData>> {
        return loginDataDaoSecure.getAllLoginData()
    }

    override suspend fun getLoginDataByKey(key: Int): LoginScreenData {
        return loginDataDaoSecure.getLoginDataByKey(key).toAddNewLoginScreenData()
    }
}
