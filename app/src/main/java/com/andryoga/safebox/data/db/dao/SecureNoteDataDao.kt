package com.andryoga.safebox.data.db.dao

import androidx.room.*
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureNoteDataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity)

    @Update
    suspend fun updateSecretNoteData(secureNoteDataEntity: SecureNoteDataEntity)

    @Query("select * from secure_note_data")
    fun getAllSecretNoteData(): Flow<List<SearchSecureNoteData>>

    @Query("select * from secure_note_data where `key` = :key limit 1")
    suspend fun getSecretNoteDataByKey(key: Int): SecureNoteDataEntity
}
