package com.andryoga.safebox.data.db.dao

import androidx.room.*
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoginDataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLoginData(loginDataEntity: LoginDataEntity)

    @Update
    suspend fun updateLoginData(loginDataEntity: LoginDataEntity)

    @Query("select * from login_data order by title")
    fun getAllLoginData(): Flow<List<SearchLoginData>>

    @Query("select * from login_data where `key` = :key limit 1")
    suspend fun getLoginDataByKey(key: Int): LoginDataEntity

    @Query("Delete from login_data where `key` = :key")
    suspend fun deleteLoginDataByKey(key: Int)

    @Query("select * from login_data")
    suspend fun exportAllData(): List<ExportLoginData>
}
