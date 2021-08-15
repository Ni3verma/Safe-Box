package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.ui.view.home.addNewData.bankCard.AddNewBankCardScreenData
import kotlinx.coroutines.flow.Flow

interface BankCardDataRepository {
    suspend fun insertBankCardData(addNewBankCardScreenData: AddNewBankCardScreenData)
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>
}
