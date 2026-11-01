package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import java.util.Date
import javax.inject.Inject

/**
 * The hint is the one field touched outside a session: it is read on the login screen, written by
 * signup before any session exists and rewritten by the login-screen reset while locked. Its
 * legacy form (`crypto_version = 1`) is therefore encrypted and decrypted with the legacy Keystore
 * handle from [KeystoreGateway.legacyVaultKey] directly, never through the
 * session-bound `SymmetricKeyUtils`; an envelope vault (`2`) stores it in plaintext.
 */
class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val vaultMetaDao: VaultMetaDao,
    private val keystoreGateway: KeystoreGateway,
) : UserDetailsDao {
    override suspend fun insertUserDetailsData(userDetailsEntity: UserDetailsEntity) {
        val storedHint = userDetailsEntity.hint?.let { storedForm(it) }
        userDetailsDao.insertUserDetailsData(userDetailsEntity.copy(hint = storedHint))
    }

    /**
     * @return The hint in plaintext, or `null` when none was stored or when a legacy vault's key is
     * gone from the Keystore. The lost key is not repaired here: the login that follows reports it
     * (`UserAuthState.LEGACY_KEY_LOST`), and a hint nobody can read is not worth a crash.
     */
    override suspend fun getHint(): String? {
        val storedHint = userDetailsDao.getHint() ?: return null
        if (isEnvelopeVault()) return storedHint
        // TODO(D3): legacy vault (crypto_version = 1) only — its hint is ciphertext under the legacy key
        val legacyKey = keystoreGateway.legacyVaultKey() ?: return null
        return AesGcmFieldCipher.decrypt(storedHint, legacyKey)
    }

    override suspend fun getUid(): String {
        return userDetailsDao.getUid()
    }

    override suspend fun updateHint(hint: String, updateDate: Date) {
        userDetailsDao.updateHint(storedForm(hint), updateDate)
    }

    override suspend fun getUserCount(): Int {
        return userDetailsDao.getUserCount()
    }

    /**
     * The hint's on-disk form follows `vault_meta.crypto_version`: ciphertext under
     * the legacy key for a legacy vault, plaintext for an envelope vault. Writers consult the row
     * too, so it must be written before the hint inside the same transaction.
     *
     * @throws IllegalStateException When a legacy vault's key is gone: there is no key to write
     * under, and writing plaintext into a `crypto_version = 1` vault would be read back as
     * ciphertext.
     */
    // TODO(MR7): the last legacy writer goes when change-password is disabled in a legacy session; signup (MR5) and the legacy reset (MR5, straight into the migration) stop writing legacy hints before that
    private suspend fun storedForm(hint: String): String {
        if (isEnvelopeVault()) return hint
        val legacyKey = checkNotNull(keystoreGateway.legacyVaultKey()) {
            "legacy vault key is missing; the hint of a crypto_version = 1 vault cannot be written"
        }
        return AesGcmFieldCipher.encrypt(hint, legacyKey)
    }

    /** A missing `crypto_version` row is read as legacy: it is the only state that predates the row. */
    // TODO(D3): legacy vault (crypto_version = 1) only — goes when one crypto_version is left
    private suspend fun isEnvelopeVault(): Boolean {
        return vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) == CryptoVersion.ENVELOPE
    }
}
