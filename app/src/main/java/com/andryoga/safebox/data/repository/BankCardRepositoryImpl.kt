package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankCardRepository
import com.andryoga.safebox.ui.view.home.addNewData.bankCard.AddNewBankCardScreenData
import java.util.*
import javax.inject.Inject

class BankCardRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure
) : BankCardRepository {
    override suspend fun insertBankCardData(addNewBankCardScreenData: AddNewBankCardScreenData) {
        val entity = BankCardDataEntity(
            addNewBankCardScreenData.title,
            addNewBankCardScreenData.name,
            addNewBankCardScreenData.number,
            addNewBankCardScreenData.pin,
            addNewBankCardScreenData.cvv,
            addNewBankCardScreenData.linkedBankAccount,
            addNewBankCardScreenData.expiryDate,
            addNewBankCardScreenData.notes,
            Date(),
            Date()
        )
        bankCardDataDaoSecure.insertBankCardData(entity)
    }
}
