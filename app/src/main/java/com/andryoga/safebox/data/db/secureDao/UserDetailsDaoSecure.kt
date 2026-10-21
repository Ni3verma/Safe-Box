package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import java.util.Date
import javax.inject.Inject

class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val vaultMetaDao: VaultMetaDao,
    private val symmetricKeyUtils: SymmetricKeyUtils,
) : UserDetailsDao {
    override suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity) {
        val storedHint = userDetailsEntity.hint?.let { storedForm(it) }
        userDetailsDao.insertUserDetailsData(userDetailsEntity.copy(hint = storedHint))
    }

    override suspend fun getHint(): String? {
        val storedHint = userDetailsDao.getHint() ?: return null
        // TODO(MR3): the legacy branch decrypts with the ambient key; MR 3 reads it through keystoreGateway.legacyVaultKey() because the holder is locked before login
        return if (isEnvelopeVault()) storedHint else symmetricKeyUtils.decrypt(storedHint)
    }

    override suspend fun getUid(): String {
        return userDetailsDao.getUid()
    }

    override suspend fun updateHint(hint: String, updateDate: Date) {
        userDetailsDao.updateHint(storedForm(hint), updateDate)
    }

    /**
     * The hint's on-disk form follows `vault_meta.crypto_version` (design §4.3): ciphertext under
     * the ambient key for a legacy vault, plaintext for an envelope vault. Writers consult the row
     * too, so it must be written before the hint inside the same transaction.
     */
    private suspend fun storedForm(hint: String): String {
        return if (isEnvelopeVault()) hint else symmetricKeyUtils.encrypt(hint)
    }

    /** A missing `crypto_version` row is read as legacy: it is the only state that predates the row. */
    private suspend fun isEnvelopeVault(): Boolean {
        // TODO(MR5): crypto_version = 2 becomes reachable once signup and the migration write it
        return vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) == CryptoVersion.ENVELOPE
    }
}
