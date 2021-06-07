package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoginDataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoginData(loginDataEntity: LoginDataEntity)

    @Query("select * from login_data")
    fun getAllLoginData(): Flow<List<LoginDataEntity>>

    @Query("select * from login_data where `key` = :key limit 1")
    fun getLoginDataByKey(key: Int): Flow<LoginDataEntity>
}
