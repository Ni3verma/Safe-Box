package com.andryoga.safebox.data.db.dao

import androidx.room.*
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)

    @Delete
    suspend fun deleteBackupMetadata(backupMetadataEntity: BackupMetadataEntity)

    @Query("update backup_metadata set lastBackupDate=:date")
    suspend fun updateLastBackupDate(date: Long)

    @Query("select * from backup_metadata where `key` = 1")
    fun getBackupMetadata(): Flow<BackupMetadataEntity?>
}
