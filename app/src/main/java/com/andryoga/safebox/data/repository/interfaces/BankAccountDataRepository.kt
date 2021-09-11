package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.BankAccountScreenData
import kotlinx.coroutines.flow.Flow

interface BankAccountDataRepository {
    suspend fun insertBankAccountData(bankAccountScreenData: BankAccountScreenData)
    suspend fun updateBankAccountData(bankAccountScreenData: BankAccountScreenData)
    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>
    suspend fun getBankAccountDataByKey(key: Int): BankAccountScreenData
    suspend fun deleteBankAccountDataByKey(key: Int)
}
