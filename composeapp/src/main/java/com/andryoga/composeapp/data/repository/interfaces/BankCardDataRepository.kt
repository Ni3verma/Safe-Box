package com.andryoga.composeapp.data.repository.interfaces

import com.andryoga.composeapp.ui.core.models.CardData

interface BankCardDataRepository {
    suspend fun upsertBankCardData(cardData: CardData)
//    fun getAllBankCardData(): Flow<List<SearchBankCardData>>
//    suspend fun getBankCardDataByKey(key: Int): BankCardScreenData
//    suspend fun deleteBankCardDataByKey(key: Int)
//    suspend fun getViewBankCardDataByKey(key: Int): ViewBankCardData
}
