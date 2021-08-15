package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankCardDataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankCardData(bankCardDataEntity: BankCardDataEntity)

    @Query("select * from bank_card_data")
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>

    @Query("select * from bank_card_data where `key` = :key limit 1")
    fun getBankCardDataByKey(key: Int): Flow<BankCardDataEntity>
}
