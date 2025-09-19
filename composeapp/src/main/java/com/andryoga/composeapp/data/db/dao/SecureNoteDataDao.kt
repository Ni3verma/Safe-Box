package com.andryoga.composeapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.composeapp.data.db.docs.SearchSecureNoteData
import com.andryoga.composeapp.data.db.docs.export.ExportSecureNoteData
import com.andryoga.composeapp.data.db.entity.SecureNoteDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureNoteDataDao {
    @Upsert
    suspend fun upsertSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMultipleSecureNoteData(secureNoteDataEntity: List<SecureNoteDataEntity>)

    @Query("select * from secure_note_data order by title")
    fun getAllSecretNoteData(): Flow<List<SearchSecureNoteData>>

    @Query("select * from secure_note_data where `key` = :key limit 1")
    suspend fun getSecretNoteDataByKey(key: Int): SecureNoteDataEntity

    @Query("Delete from secure_note_data where `key` = :key")
    suspend fun deleteSecretNoteDataByKey(key: Int)

    @Query("select * from secure_note_data")
    suspend fun exportAllData(): List<ExportSecureNoteData>

    @Query("delete from secure_note_data")
    fun deleteAllData()
}
