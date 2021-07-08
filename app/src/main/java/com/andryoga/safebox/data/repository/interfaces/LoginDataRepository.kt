package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.ui.view.home.addNewData.login.AddNewLoginScreenData
import kotlinx.coroutines.flow.Flow

interface LoginDataRepository {
    suspend fun insertLoginData(addNewLoginScreenData: AddNewLoginScreenData)
    suspend fun getAllLoginData(): Flow<List<SearchLoginData>>
}
