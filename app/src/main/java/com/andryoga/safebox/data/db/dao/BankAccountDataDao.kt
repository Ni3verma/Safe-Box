package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDataDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankAccountData(bankAccountDataEntity: BankAccountDataEntity)

    @Query("select * from bank_account_data")
    fun getAllBankAcountData(): Flow<List<BankAccountDataEntity>>

    @Query("select * from bank_account_data where `key` = :key limit 1")
    fun getBankAccountDataByKey(key: Int): Flow<BankAccountDataEntity>
}
