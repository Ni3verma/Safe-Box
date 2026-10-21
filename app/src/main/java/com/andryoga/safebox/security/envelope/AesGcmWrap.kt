package com.andryoga.safebox.security.envelope

import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encodes and decodes versioned AES-256-GCM blobs of the form `0x01 || iv(12) || ciphertext+tag`.
 */
object AesGcmWrap {
    const val VERSION_1: Byte = 0x01
    const val IV_SIZE_BYTES = 12
    const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    private const val MIN_BLOB_SIZE_BYTES = 1 + IV_SIZE_BYTES + GCM_TAG_BYTES
    private const val KEY_SIZE_BYTES = 32
    private const val KEY_ALGORITHM_AES = "AES"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Encrypts [plaintext] under [keyBytes] with [aad] and returns Base64(`0x01 || iv(12) || ct+tag`).
     *
     * @param keyBytes 32-byte AES-256 key.
     * @param plaintext Bytes to encrypt.
     * @param aad Associated authenticated data bound to the GCM tag.
     * @return Base64-encoded versioned ciphertext blob.
     */
    fun wrap(keyBytes: ByteArray, plaintext: ByteArray, aad: ByteArray): String {
        require(keyBytes.size == KEY_SIZE_BYTES) {
            "AES-256 key must be $KEY_SIZE_BYTES bytes, got ${keyBytes.size}"
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM_AES)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size == IV_SIZE_BYTES) { "Expected $IV_SIZE_BYTES-byte GCM IV, got ${iv.size}" }
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)

        val output = ByteArray(1 + iv.size + ciphertext.size)
        output[0] = VERSION_1
        System.arraycopy(iv, 0, output, 1, iv.size)
        System.arraycopy(ciphertext, 0, output, 1 + iv.size, ciphertext.size)
        return Base64.getEncoder().encodeToString(output)
    }

    /**
     * Decrypts a Base64-encoded `0x01 || iv(12) || ct+tag` [blob] under [keyBytes] with [aad].
     *
     * @param keyBytes 32-byte AES-256 key.
     * @param blob Base64-encoded blob produced by [wrap].
     * @param aad Associated authenticated data expected by the GCM tag.
     * @return Decrypted plaintext bytes.
     * @throws AEADBadTagException When the key, AAD, or ciphertext is invalid.
     * @throws IllegalArgumentException When the blob is truncated, not valid Base64, or has an unknown version.
     */
    fun unwrap(keyBytes: ByteArray, blob: String, aad: ByteArray): ByteArray {
        require(keyBytes.size == KEY_SIZE_BYTES) {
            "AES-256 key must be $KEY_SIZE_BYTES bytes, got ${keyBytes.size}"
        }
        val raw = try {
            Base64.getDecoder().decode(blob)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid Base64 in AES-GCM wrap blob", e)
        }
        require(raw.size >= MIN_BLOB_SIZE_BYTES) {
            "Truncated AES-GCM wrap blob: ${raw.size} bytes"
        }
        require(raw[0] == VERSION_1) {
            "Unsupported AES-GCM wrap version: ${raw[0]}"
        }
        val iv = raw.copyOfRange(1, 1 + IV_SIZE_BYTES)
        val ciphertext = raw.copyOfRange(1 + IV_SIZE_BYTES, raw.size)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM_AES)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
