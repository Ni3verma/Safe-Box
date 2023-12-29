package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.DomainMappers.toViewBankCardData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.ViewBankCardData
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardDataEntity
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardScreenData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankCardDataRepositoryImpl
    @Inject
    constructor(
        private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    ) : BankCardDataRepository {
        override suspend fun insertBankCardData(bankCardScreenData: BankCardScreenData) {
            bankCardDataDaoSecure.insertBankCardData(bankCardScreenData.toBankCardDataEntity(getCurrentDate = true))
        }

        override suspend fun updateBankCardData(bankCardScreenData: BankCardScreenData) {
            bankCardDataDaoSecure.updateBankCardData(bankCardScreenData.toBankCardDataEntity(getCurrentDate = false))
        }

        override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
            return bankCardDataDaoSecure.getAllBankCardData()
        }

        override suspend fun getBankCardDataByKey(key: Int): BankCardScreenData {
            return bankCardDataDaoSecure.getBankCardDataByKey(key).toBankCardScreenData()
        }

        override suspend fun deleteBankCardDataByKey(key: Int) {
            bankCardDataDaoSecure.deleteBankCardDataByKey(key)
        }

        override suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData {
            return bankCardDataDaoSecure.getBankCardDataByKey(key).toViewBankCardData()
        }
    }
