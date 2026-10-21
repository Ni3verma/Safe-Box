package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.andryoga.safebox.data.db.entity.QuarantinedRecordEntity

/**
 * Access to `quarantined_record`. Rows are only ever inserted by the crypto migration and only
 * ever removed all at once by the vault erase; there is deliberately no per-row delete or update.
 */
// TODO(MR5): insert and count are used by LegacyVaultMigrator and VaultKeyMaintenance; deleteAll by the erase flow (MR 6)
@Dao
interface QuarantinedRecordDao {
    @Insert
    suspend fun insert(entity: QuarantinedRecordEntity)

    @Query("select count(*) from quarantined_record")
    suspend fun count(): Int

    @Query("delete from quarantined_record")
    suspend fun deleteAll()
}
