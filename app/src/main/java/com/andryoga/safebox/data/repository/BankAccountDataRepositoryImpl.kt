package com.andryoga.safebox.data.repository

import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.ui.view.home.addNewData.bankAccount.AddNewBankAccountScreenData
import java.util.*
import javax.inject.Inject

class BankAccountDataRepositoryImpl @Inject constructor(
    private val bankAccountDataDaoSecure: BankAccountDataDaoSecure
) : BankAccountDataRepository {
    override suspend fun insertBankAccountData(addNewBankAccountScreenData: AddNewBankAccountScreenData) {
        val entity = BankAccountDataEntity(
            addNewBankAccountScreenData.title,
            addNewBankAccountScreenData.accountNo,
            addNewBankAccountScreenData.customerName,
            addNewBankAccountScreenData.customerId,
            addNewBankAccountScreenData.branchCode,
            addNewBankAccountScreenData.branchName,
            addNewBankAccountScreenData.branchAddress,
            addNewBankAccountScreenData.ifscCode,
            addNewBankAccountScreenData.micrCode,
            addNewBankAccountScreenData.notes,
            Date(),
            Date()
        )
        bankAccountDataDaoSecure.insertBankAccountData(entity)
    }
}
