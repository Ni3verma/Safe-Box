package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.ui.view.home.addNewData.login.AddNewLoginScreenData

interface LoginDataRepository {
    suspend fun insertLoginData(addNewLoginScreenData: AddNewLoginScreenData)
}
