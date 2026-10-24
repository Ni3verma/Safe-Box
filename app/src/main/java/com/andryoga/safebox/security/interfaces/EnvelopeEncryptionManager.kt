package com.andryoga.safebox.security.interfaces

import com.andryoga.safebox.security.envelope.KdfParams
import java.security.GeneralSecurityException
import javax.crypto.Cipher

/**
 * Indicates that unwrapping the password-wrapped DEK failed its AES-GCM authentication tag check.
 */
class WrongPasswordException(
    cause: Throwable? = null,
) : GeneralSecurityException("Incorrect master password", cause)

/**
 * High-level envelope-encryption operations for minting, wrapping, unwrapping, and verifying the DEK.
 */
interface EnvelopeEncryptionManager {
    /**
     * Generates a fresh 32-byte random AES-256 Data Encryption Key.
     */
    fun mintDek(): ByteArray

    /**
     * Generates fresh [KdfParams] with a new 16-byte random salt and the current iteration count.
     */
    fun deriveKdfParams(): KdfParams

    /**
     * Derives `KEK_Password` from [password] and [params], wraps [dek] with AES-256-GCM, and clears [password].
     *
     * @param password Master password characters; zeroed before this method returns.
     * @param params KDF parameters governing key derivation.
     * @param dek 32-byte Data Encryption Key to wrap.
     * @return Base64-encoded `wrapped_dek_password` blob.
     */
    fun wrapWithPassword(password: CharArray, params: KdfParams, dek: ByteArray): String

    /**
     * Builds the `dek_verifier` blob under [dek] so non-authenticated RSA unwraps can be verified.
     *
     * @param dek 32-byte Data Encryption Key.
     * @return Base64-encoded `dek_verifier` blob.
     */
    fun makeVerifier(dek: ByteArray): String

    /**
     * Derives `KEK_Password` from [password] and [params] and unwraps [blob], mapping tag failures
     * to [WrongPasswordException] inside a failed [Result].
     *
     * @param password Master password characters; zeroed before this method returns.
     * @param params KDF parameters governing key derivation.
     * @param blob Base64-encoded `wrapped_dek_password` blob.
     * @return [Result] holding the 32-byte DEK or [WrongPasswordException] on a wrong password.
     */
    fun unwrapWithPassword(password: CharArray, params: KdfParams, blob: String): Result<ByteArray>

    /**
     * Returns an initialized `DECRYPT_MODE` RSA-OAEP [Cipher] for [spec].
     *
     * @param spec Wrapping key specification (`BIOMETRIC` or `DEVICE`).
     * @param aliasSuffix Optional per-generation suffix from `vault_meta` (debt D13).
     * @return [Cipher] ready for `CryptoObject` binding or immediate time-bound unwrap.
     */
    fun decryptCipherFor(spec: WrapKeySpec, aliasSuffix: String? = null): Cipher

    /**
     * Unwraps a Base64-encoded RSA-OAEP wrapped DEK [blob] using an authenticated [cipher].
     *
     * @param cipher Authenticated `DECRYPT_MODE` RSA-OAEP cipher.
     * @param blob Base64-encoded `wrapped_dek_biometric` or `wrapped_dek_device` blob.
     * @return Unwrapped 32-byte DEK.
     */
    fun unwrapWithCipher(cipher: Cipher, blob: String): ByteArray

    /**
     * Verifies that [dek] decrypts [verifier] to the expected constant verifier payload.
     *
     * @param dek Candidate 32-byte DEK.
     * @param verifier Base64-encoded `dek_verifier` blob.
     * @return `true` if [dek] is the authentic vault DEK, `false` otherwise.
     */
    fun verify(dek: ByteArray, verifier: String): Boolean

    /**
     * Wraps [dek] with the public half of [spec], generating the Keystore key pair first if absent
     * or invalidated.
     *
     * @param spec Wrapping key specification (`BIOMETRIC` or `DEVICE`).
     * @param dek 32-byte Data Encryption Key to wrap.
     * @param aliasSuffix Optional per-generation suffix from `vault_meta` (debt D13).
     * @return Base64-encoded RSA-OAEP wrap blob.
     */
    fun wrapWithKeystoreKey(spec: WrapKeySpec, dek: ByteArray, aliasSuffix: String? = null): String
}
