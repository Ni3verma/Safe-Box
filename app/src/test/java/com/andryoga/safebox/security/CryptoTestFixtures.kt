package com.andryoga.safebox.security

import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Test double implementation of [SecretKey] for unit testing crypto components.
 */
class FakeSecretKey(
    private val algorithmName: String = "AES",
    private val keyBytes: ByteArray = ByteArray(32) { (it + 1).toByte() },
    private val formatName: String = "RAW"
) : SecretKey {
    override fun getAlgorithm(): String = algorithmName
    override fun getFormat(): String = formatName
    override fun getEncoded(): ByteArray = keyBytes.copyOf()
}

/**
 * Reusable crypto test fixtures, helpers, and sample data for unit tests.
 */
object CryptoTestFixtures {

    val TEST_AES_KEY_256: SecretKey by lazy {
        createSecretKey("AES", 256)
    }

    val TEST_AES_KEY_128: SecretKey by lazy {
        createSecretKey("AES", 128)
    }

    val TEST_SECONDARY_AES_KEY: SecretKey by lazy {
        val bytes = ByteArray(32) { (255 - it).toByte() }
        createSecretKeyFromBytes(bytes, "AES")
    }

    const val SAMPLE_PLAINTEXT = "SafeBox@TestSecretData!2026"
    const val UNICODE_PLAINTEXT = "🔒 SafeBox 鍵 mật khẩu café 🚀"
    const val EMPTY_PLAINTEXT = ""

    fun createSecretKey(algorithm: String = "AES", sizeBits: Int = 256): SecretKey {
        val keyGen = KeyGenerator.getInstance(algorithm)
        keyGen.init(sizeBits)
        return keyGen.generateKey()
    }

    fun createSecretKeyFromBytes(bytes: ByteArray, algorithm: String = "AES"): SecretKey {
        return SecretKeySpec(bytes, algorithm)
    }

    fun generateRandomBytes(size: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
}
