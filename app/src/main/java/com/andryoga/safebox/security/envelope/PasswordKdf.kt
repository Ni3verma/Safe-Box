package com.andryoga.safebox.security.envelope

import java.nio.CharBuffer
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives 256-bit key-encryption keys from user passwords using NFKC normalization and PBKDF2.
 *
 * NFKC normalization is applied once inside [deriveKey] so visually identical inputs from different
 * keyboards or IMEs produce the same key bytes.
 */
object PasswordKdf {
    /**
     * Calibrated on the Pixel 6a (Android 15) as the largest round iteration count for
     * `PBKDF2WithHmacSHA256` (256-bit output) that completes within the 500 ms latency budget
     * (measured 504.5 ms per 100,000 iterations in ADR-0011 S6/S10 -> 99,000 iterations ≈ 499.5 ms).
     */
    const val PBKDF2_ITERATIONS = 99_000

    /** Output length in bits; sized to the AES-256 key that [AesGcmWrap] accepts. */
    const val KEY_LENGTH_BITS = AesGcmWrap.KEY_SIZE_BYTES * 8
    private const val ASCII_LIMIT = 0x80
    private val secureRandom = SecureRandom()

    /**
     * Creates fresh [KdfParams] with a new 16-byte cryptographic salt and [PBKDF2_ITERATIONS].
     */
    fun newParams(iterations: Int = PBKDF2_ITERATIONS): KdfParams {
        val salt = ByteArray(KdfParams.SALT_SIZE_BYTES)
        secureRandom.nextBytes(salt)
        return KdfParams(
            algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
            iterations = iterations,
            salt = salt,
        )
    }

    /**
     * Derives a 256-bit key from [password] and [params] after NFKC normalization, zeroing
     * [password] and all intermediate character buffers before returning. A non-ASCII password
     * also passes through the immutable `String` that `Normalizer` returns, which cannot be
     * zeroed; an ASCII password never does.
     *
     * @param password Raw password characters; cleared to `\u0000` in `finally`.
     * @param params KDF parameters specifying algorithm, iteration count, and salt.
     * @return Derived 32-byte key material.
     */
    fun deriveKey(password: CharArray, params: KdfParams): ByteArray = deriveKey(
        password = password,
        salt = params.salt,
        iterations = params.iterations,
        algorithm = params.algorithm,
        keyLengthBits = KEY_LENGTH_BITS,
    )

    /**
     * Low-level PBKDF2 derivation after NFKC normalization, exposed within the package so RFC 6070
     * test vectors with custom salt lengths, algorithms, or bit lengths can be verified directly.
     *
     * @param password Raw password characters; cleared to `\u0000` in `finally`.
     * @param salt Salt bytes.
     * @param iterations PBKDF2 iteration count.
     * @param algorithm JCA `SecretKeyFactory` algorithm name.
     * @param keyLengthBits Output key length in bits.
     * @return Derived key bytes.
     */
    internal fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        algorithm: String = KdfParams.ALGORITHM_PBKDF2_SHA256,
        keyLengthBits: Int = KEY_LENGTH_BITS,
    ): ByteArray {
        var normalizedChars: CharArray? = null
        var keySpec: PBEKeySpec? = null
        try {
            // NFKC leaves ASCII unchanged, and skipping it avoids the String copy that normalize()
            // returns, which nothing can zero.
            normalizedChars = if (password.all { it.code < ASCII_LIMIT }) {
                password.copyOf()
            } else {
                Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFKC).toCharArray()
            }
            keySpec = PBEKeySpec(normalizedChars, salt, iterations, keyLengthBits)
            val factory = SecretKeyFactory.getInstance(algorithm)
            return factory.generateSecret(keySpec).encoded
        } finally {
            password.fill('\u0000')
            normalizedChars?.fill('\u0000')
            keySpec?.clearPassword()
        }
    }
}
