package com.andryoga.safebox.data.repository

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.domain.mappers.record.toCardData
import com.andryoga.safebox.domain.mappers.record.toDbEntity
import com.andryoga.safebox.domain.models.record.CardData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BankCardDataRepositoryImpl @Inject constructor(
    private val bankCardDataDaoSecure: BankCardDataDaoSecure,
    private val analyticsHelper: AnalyticsHelper
) : BankCardDataRepository {
    override suspend fun upsertBankCardData(cardData: CardData) {
        if (cardData.id == null || cardData.id == 0) {
            analyticsHelper.logEvent(AnalyticsKey.NEW_BANK_CARD)
        }
        bankCardDataDaoSecure.upsertBankCardData(cardData.toDbEntity())
    }

    override fun getAllBankCardData(): Flow<List<SearchBankCardData>> {
        return bankCardDataDaoSecure.getAllBankCardData()
    }

    override suspend fun getBankCardDataByKey(key: Int): CardData {
        return bankCardDataDaoSecure.getBankCardDataByKey(key).toCardData()
    }

    override suspend fun deleteBankCardDataByKey(key: Int) {
        bankCardDataDaoSecure.deleteBankCardDataByKey(key)
    }
}
