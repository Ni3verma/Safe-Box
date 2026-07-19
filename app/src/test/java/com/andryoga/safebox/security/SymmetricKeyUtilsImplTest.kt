package com.andryoga.safebox.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

class SymmetricKeyUtilsImplTest {

    @get:Rule
    val base64Rule = Base64Rule()

    private lateinit var secretKey: SecretKey
    private lateinit var symmetricKeyUtils: SymmetricKeyUtilsImpl

    @Before
    fun setUp() {
        secretKey = CryptoTestFixtures.TEST_AES_KEY_256
        symmetricKeyUtils = SymmetricKeyUtilsImpl(secretKey)
    }

    @Test
    fun encryptAndDecrypt_withStandardAsciiText_returnsOriginalPlaintext() {
        val plaintext = CryptoTestFixtures.SAMPLE_PLAINTEXT

        val encrypted = symmetricKeyUtils.encrypt(plaintext)
        val decrypted = symmetricKeyUtils.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withUnicodeAndSpecialCharacters_returnsOriginalPlaintext() {
        val plaintext = CryptoTestFixtures.UNICODE_PLAINTEXT

        val encrypted = symmetricKeyUtils.encrypt(plaintext)
        val decrypted = symmetricKeyUtils.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withEmptyString_returnsEmptyString() {
        val plaintext = CryptoTestFixtures.EMPTY_PLAINTEXT

        val encrypted = symmetricKeyUtils.encrypt(plaintext)
        val decrypted = symmetricKeyUtils.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun encryptAndDecrypt_withLargeTextPayload_returnsOriginalPlaintext() {
        val largePlaintext = "A".repeat(10_000)

        val encrypted = symmetricKeyUtils.encrypt(largePlaintext)
        val decrypted = symmetricKeyUtils.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(largePlaintext)
    }

    @Test
    fun encrypt_returnsCiphertextContainingSingleSeparator() {
        val plaintext = "TestPayload"

        val encrypted = symmetricKeyUtils.encrypt(plaintext)
        val parts = encrypted.split(SymmetricKeyUtilsImpl.Constants.separator)

        assertThat(parts).hasSize(2)
        assertThat(parts[0]).isNotEmpty()
        assertThat(parts[1]).isNotEmpty()
    }

    @Test
    fun consecutiveEncryptCalls_forSamePlaintext_produceDifferentCiphertextsDueToRandomIv() {
        val plaintext = "ConsistentPlaintext"

        val encryptedFirst = symmetricKeyUtils.encrypt(plaintext)
        val encryptedSecond = symmetricKeyUtils.encrypt(plaintext)

        assertThat(encryptedFirst).isNotEqualTo(encryptedSecond)
        assertThat(symmetricKeyUtils.decrypt(encryptedFirst)).isEqualTo(plaintext)
        assertThat(symmetricKeyUtils.decrypt(encryptedSecond)).isEqualTo(plaintext)
    }

    @Test
    fun decrypt_withMissingSeparator_throwsIndexOutOfBoundsException() {
        val malformedEncryptedString = "InvalidEncryptedPayloadWithoutSeparator"

        assertThrows(IndexOutOfBoundsException::class.java) {
            symmetricKeyUtils.decrypt(malformedEncryptedString)
        }
    }

    @Test
    fun decrypt_withCorruptedBase64Ciphertext_throwsIllegalArgumentException() {
        val validEncrypted = symmetricKeyUtils.encrypt("SampleData")
        val parts = validEncrypted.split(SymmetricKeyUtilsImpl.Constants.separator)
        val corruptedPayload =
            "###NotValidBase64###" + SymmetricKeyUtilsImpl.Constants.separator + parts[1]

        assertThrows(IllegalArgumentException::class.java) {
            symmetricKeyUtils.decrypt(corruptedPayload)
        }
    }

    @Test
    fun decrypt_withCorruptedBase64Iv_throwsIllegalArgumentException() {
        val validEncrypted = symmetricKeyUtils.encrypt("SampleData")
        val parts = validEncrypted.split(SymmetricKeyUtilsImpl.Constants.separator)
        val corruptedPayload =
            parts[0] + SymmetricKeyUtilsImpl.Constants.separator + "###NotValidBase64###"

        assertThrows(IllegalArgumentException::class.java) {
            symmetricKeyUtils.decrypt(corruptedPayload)
        }
    }

    @Test
    fun decrypt_withTamperedCiphertext_throwsGeneralSecurityException() {
        val validEncrypted = symmetricKeyUtils.encrypt("SensitiveData")
        val parts = validEncrypted.split(SymmetricKeyUtilsImpl.Constants.separator)

        val ciphertextBytes = SecurityUtils.decodeBase64(parts[0])
        ciphertextBytes[0] = (ciphertextBytes[0].toInt() xor 0xFF).toByte()
        val tamperedCiphertext = SecurityUtils.encodeBase64(ciphertextBytes)
        val tamperedEncryptedString =
            tamperedCiphertext + SymmetricKeyUtilsImpl.Constants.separator + parts[1]

        assertThrows(GeneralSecurityException::class.java) {
            symmetricKeyUtils.decrypt(tamperedEncryptedString)
        }
    }

    @Test
    fun decrypt_withWrongSecretKey_throwsGeneralSecurityException() {
        val plaintext = "SecretVaultInfo"
        val encrypted = symmetricKeyUtils.encrypt(plaintext)

        val wrongKey = CryptoTestFixtures.TEST_SECONDARY_AES_KEY
        val wrongKeyUtils = SymmetricKeyUtilsImpl(wrongKey)

        assertThrows(GeneralSecurityException::class.java) {
            wrongKeyUtils.decrypt(encrypted)
        }
    }

    @Test
    fun encryptAndDecrypt_withFakeSecretKeyDouble_returnsOriginalPlaintext() {
        val fakeKey = FakeSecretKey(algorithmName = "AES", keyBytes = ByteArray(32) { 7 })
        val utilsWithFakeKey = SymmetricKeyUtilsImpl(fakeKey)
        val plaintext = "FakeSecretKeyTest"

        val encrypted = utilsWithFakeKey.encrypt(plaintext)
        val decrypted = utilsWithFakeKey.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }
}
