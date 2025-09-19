package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.ui.core.models.BankAccountData

interface BankAccountDataRepository {
    suspend fun upsertBankAccountData(accountData: BankAccountData)
//    suspend fun updateBankAccountData(bankAccountScreenData: BankAccountScreenData)
//    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>
//    suspend fun getBankAccountDataByKey(key: Int): BankAccountScreenData
//    suspend fun deleteBankAccountDataByKey(key: Int)
//    suspend fun getViewBankAccountDataByKey(key: Int): ViewBankAccountData
}
