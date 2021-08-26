package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.AddNewBankAccountScreenData
import kotlinx.coroutines.flow.Flow

interface BankAccountDataRepository {
    suspend fun insertBankAccountData(addNewBankAccountScreenData: AddNewBankAccountScreenData)
    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>
}
