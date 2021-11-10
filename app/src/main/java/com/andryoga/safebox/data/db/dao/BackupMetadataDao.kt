package com.andryoga.safebox.data.db.dao

import androidx.room.*
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity

@Dao
interface BackupMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupMetadata(backupMetadataEntity: BackupMetadataEntity)

    @Delete
    suspend fun deleteBackupMetadata(backupMetadataEntity: BackupMetadataEntity)

    @Query("update backup_metadata set lastBackupDate=:date")
    suspend fun updateLastBackupDate(date: Long)

    @Query("select count(*) from backup_metadata")
    suspend fun getBackupMetadataCount(): Int
}
