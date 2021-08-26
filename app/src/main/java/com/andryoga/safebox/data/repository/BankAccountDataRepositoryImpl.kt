package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.bankAccount.BankAccountScreenData
import kotlinx.coroutines.flow.Flow
import java.util.*
import javax.inject.Inject

class BankAccountDataRepositoryImpl @Inject constructor(
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure
) : BankAccountDataRepository {
    override suspend fun insertBankAccountData(bankAccountScreenData: BankAccountScreenData) {
        val entity = BankAccountDataEntity(
            bankAccountScreenData.title,
            bankAccountScreenData.accountNo,
            bankAccountScreenData.customerName,
            bankAccountScreenData.customerId,
            bankAccountScreenData.branchCode,
            bankAccountScreenData.branchName,
            bankAccountScreenData.branchAddress,
            bankAccountScreenData.ifscCode,
            bankAccountScreenData.micrCode,
            bankAccountScreenData.notes,
            Date(),
            Date()
        )
        bankAccountDataDaoSecure.insertBankAccountData(entity)
    }

    override suspend fun updateBankAccountData(bankAccountScreenData: BankAccountScreenData) {
        TODO("Not yet implemented")
    }

    override fun getAllBankAccountData(): Flow<List<SearchBankAccountData>> {
        return bankAccountDataDaoSecure.getAllBankAccountData()
    }
}
