package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.ui.view.home.addNewData.bankAccount.AddNewBankAccountScreenData

interface BankAccountDataRepository {
    suspend fun insertBankAccountData(addNewBankAccountScreenData: AddNewBankAccountScreenData)
}
