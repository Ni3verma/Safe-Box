package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.safebox.data.db.docs.SearchAuthenticatorData
import com.andryoga.safebox.data.db.docs.export.ExportAuthenticatorData
import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Room database operations on the `authenticator_data` table.
 */
@Dao
interface AuthenticatorDataDao {
    @Upsert
    suspend fun upsertAuthenticatorData(authenticatorDataEntity: AuthenticatorDataEntity)

    // https://github.com/Ni3verma/Safe-Box/issues/236
    // TODO: Migrate bulk operations across all DAOs to suspend functions and switch RestoreDataWorker to RoomDatabase.withTransaction
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMultipleAuthenticatorData(authenticatorDataEntity: List<AuthenticatorDataEntity>)

    @Query("select * from authenticator_data")
    fun getAllAuthenticatorData(): Flow<List<SearchAuthenticatorData>>

    @Query("select * from authenticator_data where `key` = :key limit 1")
    suspend fun getAuthenticatorDataByKey(key: Int): AuthenticatorDataEntity

    @Query("Delete from authenticator_data where `key` = :key")
    suspend fun deleteAuthenticatorDataByKey(key: Int)

    @Query("select * from authenticator_data")
    suspend fun exportAllData(): List<ExportAuthenticatorData>

    // https://github.com/Ni3verma/Safe-Box/issues/236
    // TODO: Migrate to suspend function alongside other DAOs
    @Query("delete from authenticator_data")
    fun deleteAllData()
}
