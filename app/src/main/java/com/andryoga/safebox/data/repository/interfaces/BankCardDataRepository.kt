package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.domain.models.record.CardData
import kotlinx.coroutines.flow.Flow

interface BankCardDataRepository {
    suspend fun upsertBankCardData(cardData: CardData)
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>
    suspend fun getBankCardDataByKey(key: Int): CardData
    suspend fun deleteBankCardDataByKey(key: Int)
}
