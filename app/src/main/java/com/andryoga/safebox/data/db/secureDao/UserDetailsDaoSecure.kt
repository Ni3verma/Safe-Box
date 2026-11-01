package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import javax.inject.Inject

/**
 * Read side of `user_details`. The hint is the one field read outside a session (the login screen
 * shows it), so its legacy form (`crypto_version = 1`) is decrypted with the legacy Keystore handle
 * from [KeystoreGateway.legacyVaultKey] directly, never through the session-bound
 * `SymmetricKeyUtils`; an envelope vault (`2`) stores it in plaintext.
 *
 * Nothing writes a legacy hint any more: signup and every password change write an envelope vault
 * through `VaultRepository` (plaintext), and the migrator rewrites the hint inside its own
 * transaction.
 */
class UserDetailsDaoSecure @Inject constructor(
    private val userDetailsDao: UserDetailsDao,
    private val vaultMetaDao: VaultMetaDao,
    private val keystoreGateway: KeystoreGateway,
) {
    /**
     * @return The hint in plaintext, or `null` when none was stored or when a legacy vault's key is
     * gone from the Keystore. The lost key is not repaired here: the login that follows reports it
     * (`UserAuthState.LEGACY_KEY_LOST`), and a hint nobody can read is not worth a crash.
     */
    suspend fun getHint(): String? {
        val storedHint = userDetailsDao.getHint() ?: return null
        if (isEnvelopeVault()) return storedHint
        // TODO(D3): legacy vault (crypto_version = 1) only — its hint is ciphertext under the legacy key
        val legacyKey = keystoreGateway.legacyVaultKey() ?: return null
        return AesGcmFieldCipher.decrypt(storedHint, legacyKey)
    }

    suspend fun getUid(): String {
        return userDetailsDao.getUid()
    }

    /** A missing `crypto_version` row is read as legacy: it is the only state that predates the row. */
    // TODO(D3): legacy vault (crypto_version = 1) only — goes when one crypto_version is left
    private suspend fun isEnvelopeVault(): Boolean {
        return vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) == CryptoVersion.ENVELOPE
    }
}
