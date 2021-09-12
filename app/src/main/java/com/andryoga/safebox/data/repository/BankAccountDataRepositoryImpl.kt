package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.BankAccountScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.BankAccountScreenData.Companion.toBankAccountDataEntity
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.BankAccountScreenData.Companion.toBankAccountScreenData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankAccountDataRepositoryImpl @Inject constructor(
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure
) : BankAccountDataRepository {
    override suspend fun insertBankAccountData(bankAccountScreenData: BankAccountScreenData) {
        val entity = bankAccountScreenData.toBankAccountDataEntity()
        bankAccountDataDaoSecure.insertBankAccountData(entity)
    }

    override suspend fun updateBankAccountData(bankAccountScreenData: BankAccountScreenData) {
        bankAccountDataDaoSecure.updateBankAccountData(bankAccountScreenData.toBankAccountDataEntity())
    }

    override fun getAllBankAccountData(): Flow<List<SearchBankAccountData>> {
        return bankAccountDataDaoSecure.getAllBankAccountData()
    }

    override suspend fun getBankAccountDataByKey(key: Int): BankAccountScreenData {
        return bankAccountDataDaoSecure.getBankAccountDataByKey(key).toBankAccountScreenData()
    }

    override suspend fun deleteBankAccountDataByKey(key: Int) {
        bankAccountDataDaoSecure.deleteBankAccountDataByKey(key)
    }
}
