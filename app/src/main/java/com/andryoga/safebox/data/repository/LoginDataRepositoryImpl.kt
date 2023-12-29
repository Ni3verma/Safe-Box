package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.DomainMappers.toViewLoginData
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.ViewLoginData
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData.Companion.toLoginDataEntity
import com.andryoga.safebox.ui.view.home.dataDetails.login.LoginScreenData.Companion.toLoginScreenData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ExperimentalCoroutinesApi
class LoginDataRepositoryImpl
    @Inject
    constructor(
        private val loginDataDaoSecure: LoginDataDaoSecure,
    ) : LoginDataRepository {
        override suspend fun insertLoginData(loginScreenData: LoginScreenData) {
            loginDataDaoSecure.insertLoginData(loginScreenData.toLoginDataEntity(getCurrentDate = true))
        }

        override suspend fun updateLoginData(loginScreenData: LoginScreenData) {
            loginDataDaoSecure.updateLoginData(loginScreenData.toLoginDataEntity(getCurrentDate = false))
        }

        override suspend fun getAllLoginData(): Flow<List<SearchLoginData>> {
            return loginDataDaoSecure.getAllLoginData()
        }

        override suspend fun getLoginDataByKey(key: Int): LoginScreenData {
            return loginDataDaoSecure.getLoginDataByKey(key).toLoginScreenData()
        }

        override suspend fun deleteLoginDataByKey(key: Int) {
            loginDataDaoSecure.deleteLoginDataByKey(key)
        }

        override suspend fun getViewLoginDataByKey(key: Int): ViewLoginData {
            return loginDataDaoSecure.getLoginDataByKey(key).toViewLoginData()
        }
    }
