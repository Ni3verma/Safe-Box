package com.andryoga.safebox.data.db.dao

import androidx.room.*
import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDataDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankAccountData(bankAccountDataEntity: BankAccountDataEntity)

    @Update
    suspend fun updateBankAccountData(bankAccountDataEntity: BankAccountDataEntity)

    @Query("select * from bank_account_data where `key` = :key limit 1")
    suspend fun getBankAccountDataByKey(key: Int): BankAccountDataEntity

    @Query("select * from bank_account_data order by title")
    fun getAllBankAccountData(): Flow<List<SearchBankAccountData>>

    @Query("Delete from bank_account_data where `key` = :key")
    suspend fun deleteBankAccountDataByKey(key: Int)

    @Query("select * from bank_account_data")
    suspend fun exportAllData(): List<BankAccountDataEntity>
}
