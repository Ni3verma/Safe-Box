package com.andryoga.safebox.security

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The on-disk format of every encrypted record field: `b64(ciphertext) | b64(iv)`, AES-256-GCM
 * with a 128-bit tag. The format predates the envelope and is kept unchanged.
 *
 * Pure functions over an explicit [SecretKey], so the same codec serves the session key behind
 * [SymmetricKeyUtilsImpl], the legacy Keystore handle for the pre-login hint read
 * (`UserDetailsDaoSecure`) and, later, the migration that re-encrypts every field.
 */
object AesGcmFieldCipher {
    const val SEPARATOR = "|"
    const val GCM_AUTH_TAG_LENGTH_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * @param data Plaintext to protect, encoded as UTF-8.
     * @param key AES key; a Keystore handle or an in-memory key spec both work.
     * @return `b64(ciphertext)|b64(iv)` with a fresh random IV per call.
     */
    fun encrypt(data: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ivBytes = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())

        val base64EncodedData = SecurityUtils.encodeBase64(encryptedBytes)
        val base64EncodedIvData = SecurityUtils.encodeBase64(ivBytes)

        return base64EncodedData + SEPARATOR + base64EncodedIvData
    }

    /**
     * @param data A value produced by [encrypt].
     * @param key The key that produced it.
     * @return The original plaintext.
     * @throws javax.crypto.AEADBadTagException When [key] is not the key that produced [data], or
     * the ciphertext was altered.
     */
    fun decrypt(data: String, key: SecretKey): String {
        val dataArray = data.split(SEPARATOR, limit = 2)
        val base64EncodedEncryptedString = dataArray[0]
        val base64EncodedInitVector = dataArray[1]

        val encryptedByteArray = SecurityUtils.decodeBase64(base64EncodedEncryptedString)
        val initVectorByteArray = SecurityUtils.decodeBase64(base64EncodedInitVector)

        val spec = GCMParameterSpec(GCM_AUTH_TAG_LENGTH_BITS, initVectorByteArray)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val decryptedBytes = cipher.doFinal(encryptedByteArray)
        return decryptedBytes.toString(Charsets.UTF_8)
    }
}
