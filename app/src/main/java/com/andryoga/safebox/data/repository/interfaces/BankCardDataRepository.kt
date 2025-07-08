package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.ViewBankCardData
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData
import kotlinx.coroutines.flow.Flow

interface BankCardDataRepository {
    suspend fun insertBankCardData(bankCardScreenData: BankCardScreenData)
    suspend fun updateBankCardData(bankCardScreenData: BankCardScreenData)
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>
    suspend fun getBankCardDataByKey(key: Int): BankCardScreenData
    suspend fun deleteBankCardDataByKey(key: Int)
    suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData
}
