package com.andryoga.composeapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.composeapp.data.db.docs.SearchBankAccountData
import com.andryoga.composeapp.data.db.docs.export.ExportBankAccountData
import com.andryoga.composeapp.data.db.entity.BankAccountDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDataDao {

    @Upsert
    suspend fun upsertBankAccountData(bankAccountDataEntity: BankAccountDataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMultipleBankAccountData(bankAccountDataEntity: List<BankAccountDataEntity>)

    @Query("select * from bank_account_data where `key` = :key limit 1")
    suspend fun getBankAccountDataByKey(key: Int): BankAccountDataEntity

    @Query("select * from bank_account_data")
    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>

    @Query("Delete from bank_account_data where `key` = :key")
    suspend fun deleteBankAccountDataByKey(key: Int)

    @Query("select * from bank_account_data")
    suspend fun exportAllData(): List<ExportBankAccountData>

    @Query("delete from bank_account_data")
    fun deleteAllData()
}
