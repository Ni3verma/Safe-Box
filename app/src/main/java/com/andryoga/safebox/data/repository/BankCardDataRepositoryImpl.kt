package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject

class BankCardDataRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure
) : BankCardDataRepository {
    override suspend fun insertBankCardData(bankCardScreenData: BankCardScreenData) {
        val entity = BankCardDataEntity(
            bankCardScreenData.title,
            bankCardScreenData.name,
            bankCardScreenData.number,
            bankCardScreenData.pin,
            bankCardScreenData.cvv,
            bankCardScreenData.linkedBankAccount,
            bankCardScreenData.expiryDate,
            bankCardScreenData.notes,
            Date(),
            Date()
        )
        bankCardDataDaoSecure.insertBankCardData(entity)
    }

    override suspend fun updateBankCardData(bankCardScreenData: BankCardScreenData) {
        TODO("Not yet implemented")
    }

    override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
        return bankCardDataDaoSecure.getAllBankCardData()
    }
}
