package com.andryoga.composeapp.data.repository

import com.andryoga.composeapp.common.AnalyticsKeys.NEW_BANK_CARD
import com.andryoga.composeapp.data.db.docs.SearchBankCardData
import com.andryoga.composeapp.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.domain.mappers.record.toDbEntity
import com.andryoga.composeapp.ui.core.models.CardData
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankCardDataRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure
) : BankCardDataRepository {
    override suspend fun upsertBankCardData(cardData: CardData) {
        if (cardData.id == null || cardData.id == 0) {
            Firebase.analytics.logEvent(NEW_BANK_CARD, null)
        }
        bankCardDataDaoSecure.upsertBankCardData(cardData.toDbEntity())
    }

    override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
        return bankCardDataDaoSecure.getAllBankCardData()
    }
//
//    override suspend fun getBankCardDataByKey(key: Int): BankCardScreenData {
//        return bankCardDataDaoSecure.getBankCardDataByKey(key).toBankCardScreenData()
//    }
//
//    override suspend fun deleteBankCardDataByKey(key: Int) {
//        bankCardDataDaoSecure.deleteBankCardDataByKey(key)
//    }
//
//    override suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData {
//        return bankCardDataDaoSecure.getBankCardDataByKey(key).toViewBankCardData()
//    }
}
