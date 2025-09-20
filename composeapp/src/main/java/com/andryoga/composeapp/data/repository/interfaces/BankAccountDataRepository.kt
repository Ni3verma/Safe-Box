package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.data.db.docs.SearchBankAccountData
import com.andryoga.composeapp.ui.core.models.BankAccountData
import kotlinx.coroutines.flow.Flow

interface BankAccountDataRepository {
    suspend fun upsertBankAccountData(accountData: BankAccountData)
    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>
//    suspend fun getBankAccountDataByKey(key: Int): BankAccountScreenData
//    suspend fun deleteBankAccountDataByKey(key: Int)
//    suspend fun getViewBankAccountDataByKey(key: Int): ViewBankAccountData
}
