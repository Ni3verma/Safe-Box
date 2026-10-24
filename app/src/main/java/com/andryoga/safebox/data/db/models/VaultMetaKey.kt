package com.andryoga.safebox.data.db.models

import com.andryoga.safebox.security.envelope.KdfParams

/**
 * Keys of the `vault_meta` table. Every key is a frozen wire value: it is written by
 * `MIGRATION_5_6` as a SQL literal and read back through these constants, so renaming one here
 * without a new migration orphans the stored row.
 */
object VaultMetaKey {
    /** Which key the record tables and the hint are under; one of [CryptoVersion]. */
    const val CRYPTO_VERSION = "crypto_version"

    /** JSON-encoded [KdfParams] (`algorithm`, `iterations`, `salt_b64`). */
    const val KDF_PARAMS = "kdf_params"

    /** Base64-encoded AES-256-GCM wrap of the DEK under `KEK_Password`. */
    const val WRAPPED_DEK_PASSWORD = "wrapped_dek_password"

    /** Base64-encoded AES-256-GCM verifier payload (`safebox.dek.verify.v1`) encrypted under the DEK. */
    const val DEK_VERIFIER = "dek_verifier"

    /** Base64-encoded RSA-2048-OAEP wrap of the DEK under `KEK_Biometric`. */
    const val WRAPPED_DEK_BIOMETRIC = "wrapped_dek_biometric"

    /** Base64-encoded RSA-2048-OAEP wrap of the DEK under `KEK_Device`. */
    const val WRAPPED_DEK_DEVICE = "wrapped_dek_device"

    /** Per-generation suffix appended to `safebox_kek_biometric` so a stuck Keystore entry can be bypassed. */
    const val BIOMETRIC_KEY_ALIAS_SUFFIX = "biometric_key_alias_suffix"

    /** Per-generation suffix appended to `safebox_kek_device` so a stuck Keystore entry can be bypassed. */
    const val DEVICE_KEY_ALIAS_SUFFIX = "device_key_alias_suffix"

    /** Set to `"1"` when the user disables biometric unlock in Settings. */
    const val BIOMETRIC_UNLOCK_DISABLED = "biometric_unlock_disabled"

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
