package com.andryoga.safebox.security.envelope.models

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
