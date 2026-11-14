package com.andryoga.safebox.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

class AesGcmFieldCipherTest {

    @get:Rule
    val base64Rule = Base64Rule()

    private lateinit var secretKey: SecretKey

    @Before
    fun setUp() {
        secretKey = CryptoTestFixtures.TEST_AES_KEY_256
    }

    @Test
    fun encryptAndDecrypt_withStandardAsciiText_returnsOriginalPlaintext() {
        val plaintext = CryptoTestFixtures.SAMPLE_PLAINTEXT

        val encrypted = AesGcmFieldCipher.encrypt(plaintext, secretKey)
        val decrypted = AesGcmFieldCipher.decrypt(encrypted, secretKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withUnicodeAndSpecialCharacters_returnsOriginalPlaintext() {
        val plaintext = CryptoTestFixtures.UNICODE_PLAINTEXT

        val encrypted = AesGcmFieldCipher.encrypt(plaintext, secretKey)
        val decrypted = AesGcmFieldCipher.decrypt(encrypted, secretKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withEmptyString_returnsEmptyString() {
        val plaintext = CryptoTestFixtures.EMPTY_PLAINTEXT

        val encrypted = AesGcmFieldCipher.encrypt(plaintext, secretKey)
        val decrypted = AesGcmFieldCipher.decrypt(encrypted, secretKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withLargeTextPayload_returnsOriginalPlaintext() {
        val largePlaintext = "A".repeat(10_000)

        val encrypted = AesGcmFieldCipher.encrypt(largePlaintext, secretKey)
        val decrypted = AesGcmFieldCipher.decrypt(encrypted, secretKey)

        assertThat(decrypted).isEqualTo(largePlaintext)
    }

    @Test
    fun encrypt_returnsCiphertextContainingSingleSeparator() {
        val plaintext = "TestPayload"

        val encrypted = AesGcmFieldCipher.encrypt(plaintext, secretKey)
        val parts = encrypted.split(AesGcmFieldCipher.SEPARATOR)

        assertThat(parts).hasSize(2)
        assertThat(parts[0]).isNotEmpty()
        assertThat(parts[1]).isNotEmpty()
    }

    @Test
    fun consecutiveEncryptCalls_forSamePlaintext_produceDifferentCiphertextsDueToRandomIv() {
        val plaintext = "ConsistentPlaintext"

        val encryptedFirst = AesGcmFieldCipher.encrypt(plaintext, secretKey)
        val encryptedSecond = AesGcmFieldCipher.encrypt(plaintext, secretKey)

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond)
        assertThat(AesGcmFieldCipher.decrypt(encryptedFirst, secretKey)).isEqualTo(plaintext)
        assertThat(AesGcmFieldCipher.decrypt(encryptedSecond, secretKey)).isEqualTo(plaintext)
    }

    @Test
    fun decrypt_withMissingSeparator_throwsIndexOutOfBoundsException() {
        val malformedEncryptedString = "InvalidEncryptedPayloadWithoutSeparator"

        assertThrows(IndexOutOfBoundsException::class.java) {
            AesGcmFieldCipher.decrypt(malformedEncryptedString, secretKey)
        }
    }

    @Test
    fun decrypt_withCorruptedBase64Ciphertext_throwsIllegalArgumentException() {
        val validEncrypted = AesGcmFieldCipher.encrypt("SampleData", secretKey)
        val parts = validEncrypted.split(AesGcmFieldCipher.SEPARATOR)
        val corruptedPayload = "###NotValidBase64###" + AesGcmFieldCipher.SEPARATOR + parts[1]

        assertThrows(IllegalArgumentException::class.java) {
            AesGcmFieldCipher.decrypt(corruptedPayload, secretKey)
        }
    }

    @Test
    fun decrypt_withCorruptedBase64Iv_throwsIllegalArgumentException() {
        val validEncrypted = AesGcmFieldCipher.encrypt("SampleData", secretKey)
        val parts = validEncrypted.split(AesGcmFieldCipher.SEPARATOR)
        val corruptedPayload = parts[0] + AesGcmFieldCipher.SEPARATOR + "###NotValidBase64###"

        assertThrows(IllegalArgumentException::class.java) {
            AesGcmFieldCipher.decrypt(corruptedPayload, secretKey)
        }
    }

    @Test
    fun decrypt_withTamperedCiphertext_throwsGeneralSecurityException() {
        val validEncrypted = AesGcmFieldCipher.encrypt("SensitiveData", secretKey)
        val parts = validEncrypted.split(AesGcmFieldCipher.SEPARATOR)

        val ciphertextBytes = SecurityUtils.decodeBase64(parts[0])
        ciphertextBytes[0] = (ciphertextBytes[0].toInt() xor 0xFF).toByte()
        val tamperedCiphertext = SecurityUtils.encodeBase64(ciphertextBytes)
        val tamperedEncryptedString = tamperedCiphertext + AesGcmFieldCipher.SEPARATOR + parts[1]

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmFieldCipher.decrypt(tamperedEncryptedString, secretKey)
        }
    }

    @Test
    fun decrypt_withWrongSecretKey_throwsGeneralSecurityException() {
        val encrypted = AesGcmFieldCipher.encrypt("SecretVaultInfo", secretKey)

        assertThrows(GeneralSecurityException::class.java) {
            AesGcmFieldCipher.decrypt(encrypted, CryptoTestFixtures.TEST_SECONDARY_AES_KEY)
        }
    }

    @Test
    fun encryptAndDecrypt_withFakeSecretKeyDouble_returnsOriginalPlaintext() {
        val fakeKey = FakeSecretKey(algorithmName = "AES", keyBytes = ByteArray(32) { 7 })
        val plaintext = "FakeSecretKeyTest"

        val encrypted = AesGcmFieldCipher.encrypt(plaintext, fakeKey)
        val decrypted = AesGcmFieldCipher.decrypt(encrypted, fakeKey)

        assertThat(decrypted).isEqualTo(plaintext)
    }
}
