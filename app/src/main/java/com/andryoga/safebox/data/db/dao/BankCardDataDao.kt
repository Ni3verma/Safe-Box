package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankCardDataDao {
    @Upsert
    suspend fun upsertBankCardData(bankCardDataEntity: BankCardDataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMultipleBankCardData(bankCardDataEntity: List<BankCardDataEntity>)

    @Query("select * from bank_card_data")
    fun getAllBankCardData(): Flow<List<SearchBankCardData>>

    @Query("select * from bank_card_data where `key` = :key limit 1")
    suspend fun getBankCardDataByKey(key: Int): BankCardDataEntity

    @Query("Delete from bank_card_data where `key` = :key")
    suspend fun deleteBankCardDataByKey(key: Int)

    @Query("select * from bank_card_data")
    suspend fun exportAllData(): List<ExportBankCardData>

    @Query("delete from bank_card_data")
    fun deleteAllData()
}
