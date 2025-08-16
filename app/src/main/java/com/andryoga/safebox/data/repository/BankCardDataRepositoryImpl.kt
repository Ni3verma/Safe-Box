package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.AnalyticsKeys.NEW_BANK_CARD
import com.andryoga.safebox.common.DomainMappers.toViewBankCardData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.ViewBankCardData
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardDataEntity
import com.andryoga.safebox.ui.view.home.dataDetails.bankCard.BankCardScreenData.Companion.toBankCardScreenData
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankCardDataRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure
) : BankCardDataRepository {
    override suspend fun insertBankCardData(bankCardScreenData: BankCardScreenData) {
        Firebase.analytics.logEvent(NEW_BANK_CARD, null)
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
