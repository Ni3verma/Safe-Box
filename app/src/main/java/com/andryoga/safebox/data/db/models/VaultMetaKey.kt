package com.andryoga.safebox.data.db.models

/**
 * Keys of the `vault_meta` table. Every key is a frozen wire value: it is written by
 * `MIGRATION_5_6` as a SQL literal and read back through these constants, so renaming one here
 * without a new migration orphans the stored row.
 */
object VaultMetaKey {
    /** Which key the record tables and the hint are under; one of [CryptoVersion]. */
    const val CRYPTO_VERSION = "crypto_version"

    /**
     * The master-password hash in today's `b64(hash)|b64(salt)` format, parked here by
     * `MIGRATION_5_6` when the `user_details.password` column was dropped. Present only while the
     * vault is [CryptoVersion.LEGACY].
     */
    // TODO(D3): legacy vault (crypto_version = 1) only (debt row D2 tracks the row itself)
    const val LEGACY_PASSWORD_HASH = "legacy_password_hash"
}

/** Values of [VaultMetaKey.CRYPTO_VERSION]. */
object CryptoVersion {
    /** Records and hint encrypted under the ambient Keystore key `symmetricDataKey`. */
    const val LEGACY = "1"

    /** Records encrypted under the session DEK; hint stored in plaintext. */
    const val ENVELOPE = "2"
}
