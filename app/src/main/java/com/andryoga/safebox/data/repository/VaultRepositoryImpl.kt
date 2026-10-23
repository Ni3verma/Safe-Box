package com.andryoga.safebox.data.repository

import com.andryoga.safebox.common.Utils
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.VaultRepository
import javax.inject.Inject

/**
 * Reads the plain DAOs, not the secure one: existence needs no decryption and must be answerable
 * before any session exists.
 */
class VaultRepositoryImpl @Inject constructor(
    private val vaultMetaDao: VaultMetaDao,
    private val userDetailsDao: UserDetailsDao,
) : VaultRepository {

    override suspend fun hasVault(): Boolean {
        if (vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) != null) return true
        if (userDetailsDao.getUserCount() == 0) return false
        // MIGRATION_5_6 stamps crypto_version for every user row and signup writes both in one
        // transaction, so a user row without it is a defect. Still a vault: Login, never Signup.
        Utils.crashInDebugBuild("user_details row without a crypto_version row")
        return true
    }
}
