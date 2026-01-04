package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoginDataDao {
    @Upsert
    suspend fun upsertLoginData(loginDataEntity: LoginDataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMultipleLoginData(loginDataEntity: List<LoginDataEntity>)

    @Query("select * from login_data")
    fun getAllLoginData(): Flow<List<SearchLoginData>>

    @Query("select * from login_data where `key` = :key limit 1")
    suspend fun getLoginDataByKey(key: Int): LoginDataEntity

    @Query("Delete from login_data where `key` = :key")
    suspend fun deleteLoginDataByKey(key: Int)

    @Query("select * from login_data")
    suspend fun exportAllData(): List<ExportLoginData>

    @Query("delete from login_data")
    fun deleteAllData()
}
