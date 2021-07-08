package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.ui.view.home.addNewData.login.AddNewLoginScreenData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject

@ExperimentalCoroutinesApi
class LoginDataRepositoryImpl @Inject constructor(
    private val loginDataDaoSecure: LoginDataDaoSecure
) : LoginDataRepository {
    override suspend fun insertLoginData(addNewLoginScreenData: AddNewLoginScreenData) {
        val entity = LoginDataEntity(
            addNewLoginScreenData.title,
            addNewLoginScreenData.url,
            addNewLoginScreenData.password,
            addNewLoginScreenData.notes,
            addNewLoginScreenData.userId,
            Date(),
            Date()
        )
        loginDataDaoSecure.insertLoginData(entity)
    }

    override suspend fun getAllLoginData(): Flow<List<SearchLoginData>> {
        return loginDataDaoSecure.getAllLoginData()
    }
}
