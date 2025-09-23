package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.data.db.docs.SearchBankCardData
import com.andryoga.composeapp.domain.models.record.CardData
import kotlinx.coroutines.flow.Flow

interface BankCardDataRepository {
    suspend fun upsertBankCardData(cardData: CardData)
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>
//    suspend fun getBankCardDataByKey(key: Int): BankCardScreenData
//    suspend fun deleteBankCardDataByKey(key: Int)
//    suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData
}
