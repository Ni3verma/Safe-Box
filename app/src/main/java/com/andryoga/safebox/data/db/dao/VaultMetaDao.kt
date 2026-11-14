package com.andryoga.safebox.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.andryoga.safebox.data.db.entity.VaultMetaEntity

/**
 * Key-value access to `vault_meta`. Callers that write more than one row wrap the writes in a
 * database transaction; the DAO itself is one row per call.
 */
@Dao
interface VaultMetaDao {
    @Upsert
    suspend fun upsert(entity: VaultMetaEntity)

    /**
     * @param key one of `VaultMetaKey`
     * @return the stored value, or `null` when the key has never been written
     */
    @Query("select `value` from vault_meta where `key` = :key")
    suspend fun get(key: String): String?

    /**
     * Deletes the row for [key] if present; no-op when absent.
     *
     * @param key one of `VaultMetaKey`
     */
    @Query("delete from vault_meta where `key` = :key")
    suspend fun delete(key: String)
}
