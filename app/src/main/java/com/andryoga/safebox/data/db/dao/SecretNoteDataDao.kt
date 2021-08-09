package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.SecretNoteDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecretNoteDataDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSecretNoteData(secretNoteDataEntity: SecretNoteDataEntity)

    @Query("select * from secret_note_data")
    fun getAllSecretNoteData(): Flow<List<SecretNoteDataEntity>>

    @Query("select * from secret_note_data where `key` = :key limit 1")
    fun getSecretNoteDataByKey(key: Int): Flow<SecretNoteDataEntity>
}
