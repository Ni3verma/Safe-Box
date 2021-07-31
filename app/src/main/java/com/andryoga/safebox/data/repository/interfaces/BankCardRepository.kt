package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.ui.view.home.addNewData.bankCard.AddNewBankCardScreenData

interface BankCardRepository {
    suspend fun insertBankCardData(addNewBankCardScreenData: AddNewBankCardScreenData)
}
