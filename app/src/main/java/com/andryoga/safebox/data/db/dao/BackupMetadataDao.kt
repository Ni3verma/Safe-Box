package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)

    @Query("delete from backup_metadata")
    suspend fun deleteBackupMetadata()

    @Query("update backup_metadata set lastBackupDate=:date")
    suspend fun updateLastBackupDate(date: Long)

    @Query("select * from backup_metadata where `key` = 1")
    fun getBackupMetadata(): Flow<BackupMetadataEntity?>
}
