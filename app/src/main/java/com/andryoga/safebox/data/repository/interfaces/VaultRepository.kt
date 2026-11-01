package com.andryoga.safebox.data.repository.interfaces

import com.andryoga.safebox.security.envelope.KdfParams
import com.andryoga.safebox.security.interfaces.WrapKeySpec

/**
 * Snapshot of the vault's persisted authentication state read before any session exists.
 */
sealed interface VaultState {
    /** A `crypto_version = 1` vault whose records and hint are encrypted under the legacy Keystore key. */
    data object Legacy : VaultState

    /**
     * A `crypto_version = 2` envelope vault.
     *
     * @property kdfParams PBKDF2 parameters for deriving `KEK_Password`.
     * @property wrappedDekPassword Base64-encoded AES-256-GCM wrap of the DEK under `KEK_Password`.
     * @property dekVerifier Base64-encoded AES-256-GCM verifier payload under the DEK.
     * @property wrappedDekBiometric Optional Base64-encoded RSA-OAEP wrap of the DEK under `KEK_Biometric`.
     * @property biometricKeyAliasSuffix Optional per-generation suffix for `safebox_kek_biometric`.
     * @property wrappedDekDevice Optional Base64-encoded RSA-OAEP wrap of the DEK under `KEK_Device`.
     * @property deviceKeyAliasSuffix Optional per-generation suffix for `safebox_kek_device`.
     * @property isBiometricUnlockDisabled True when the user disabled biometric unlock (`biometric_unlock_disabled = "1"`).
     */
    data class Envelope(
        val kdfParams: KdfParams,
        val wrappedDekPassword: String,
        val dekVerifier: String,
        val wrappedDekBiometric: String?,
        val biometricKeyAliasSuffix: String?,
        val wrappedDekDevice: String?,
        val deviceKeyAliasSuffix: String?,
        val isBiometricUnlockDisabled: Boolean,
    ) : VaultState

    /**
     * A `crypto_version = 2` vault whose mandatory `vault_meta` rows (`kdf_params`,
     * `wrapped_dek_password`, or `dek_verifier`) are missing or unparseable.
     */
    data object CorruptedEnvelope : VaultState
}

/** The vault as a whole (existence and envelope wrapping), unlike the per-type record repositories. */
interface VaultRepository {
    /**
     * Whether a vault exists on disk, from Room alone — never from a preference, so a lost or
     * flipped one cannot route an existing vault into signup.
     */
    suspend fun hasVault(): Boolean

    /**
     * Mints a fresh 32-byte DEK, wraps it under [password], writes the `crypto_version = 2`
     * `vault_meta` rows and the initial `user_details` row with plaintext [hint] in a single
     * database transaction, zeroes [password], and returns the raw 32-byte DEK.
     *
     * @param password Master password characters; zeroed before this method returns.
     * @param hint Master password hint stored in plaintext.
     * @return Newly minted 32-byte DEK for opening the initial session.
     */
    suspend fun createVault(password: CharArray, hint: String): ByteArray

    /**
     * Re-derives `KEK_Password` with fresh [KdfParams] (new random salt and current iteration
     * count), wraps [dek] under [newPassword], and atomically updates `kdf_params`,
     * `wrapped_dek_password` and the plaintext `user_details.hint` in one transaction. Records,
     * `dek_verifier` and the Keystore wraps are untouched: the DEK does not change.
     *
     * @param newPassword Master password characters; zeroed before this method returns.
     * @param dek Active 32-byte Data Encryption Key.
     * @param hint Replacement master password hint, stored in plaintext.
     */
    suspend fun rewrapPassword(newPassword: CharArray, dek: ByteArray, hint: String)

    /**
     * Persists a Keystore-wrapped DEK [blob] for [spec] and optionally updates its per-generation
     * [aliasSuffix] in a single transaction.
     *
     * @param spec Target wrapping key (`BIOMETRIC` or `DEVICE`).
     * @param blob Base64-encoded RSA-OAEP wrap of the DEK.
     * @param aliasSuffix Optional generation suffix for `safebox_kek_*`.
     */
    suspend fun storeWrap(spec: WrapKeySpec, blob: String, aliasSuffix: String? = null)

    /**
     * Deletes the stored wrap blob (`wrapped_dek_biometric` or `wrapped_dek_device`) for [spec]
     * from `vault_meta`, leaving any per-generation alias suffix intact so the next wrap rotation
     * advances monotonically.
     */
    suspend fun deleteWrap(spec: WrapKeySpec)

    /**
     * Reads the current per-generation alias suffix for [spec] from `vault_meta`, or `null` if the
     * base alias is still in use.
     */
    suspend fun getWrapKeyAliasSuffix(spec: WrapKeySpec): String?

    /**
     * Reads the persisted vault state needed before any session exists.
     */
    suspend fun getVaultState(): VaultState
}
