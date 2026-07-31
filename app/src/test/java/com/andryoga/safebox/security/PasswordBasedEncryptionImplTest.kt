package com.andryoga.safebox.security

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException

class PasswordBasedEncryptionImplTest {

    private lateinit var pbe: PasswordBasedEncryptionImpl

    @Before
    fun setUp() {
        pbe = PasswordBasedEncryptionImpl()
    }

    @Test
    fun getRandomSalt_returnsByteArrayOfConfiguredSaltSize() {
        val salt = pbe.getRandomSalt()

        assertThat(salt).hasLength(PasswordBasedEncryptionImpl.Constants.SALT_SIZE)
        assertThat(salt.any { it != 0.toByte() }).isTrue()
    }

    @Test
    fun consecutiveGetRandomSaltCalls_returnDistinctByteArrays() {
        val salt1 = pbe.getRandomSalt()
        val salt2 = pbe.getRandomSalt()

        assertThat(salt1).isNotEqualTo(salt2)
    }

    @Test
    fun getRandomIV_returnsByteArrayOfConfiguredIvSize() {
        val iv = pbe.getRandomIV()

        assertThat(iv).hasLength(PasswordBasedEncryptionImpl.Constants.IV_SIZE)
        assertThat(iv.any { it != 0.toByte() }).isTrue()
    }

    @Test
    fun consecutiveGetRandomIVCalls_returnDistinctByteArrays() {
        val iv1 = pbe.getRandomIV()
        val iv2 = pbe.getRandomIV()

        assertThat(iv1).isNotEqualTo(iv2)
    }

    @Test
    fun encryptDecrypt_roundtripWithValidInputs_returnsOriginalData() {
        val password = "StrongMasterPassword123".toCharArray()
        val data = "SensitiveDatabasePayloadToEncrypt".toByteArray(Charsets.UTF_8)
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(password, data, salt, iv, encrypt = true)
        val decrypted = pbe.encryptDecrypt(password, encrypted, salt, iv, encrypt = false)

        assertThat(decrypted).isEqualTo(data)
    }

    @Test
    fun encryptDecrypt_roundtripWithEmptyByteArray_returnsEmptyByteArray() {
        val password = "EmptyDataPassword".toCharArray()
        val data = ByteArray(0)
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(password, data, salt, iv, encrypt = true)
        val decrypted = pbe.encryptDecrypt(password, encrypted, salt, iv, encrypt = false)

        assertThat(decrypted).isEqualTo(data)
    }

    @Test
    fun encryptDecrypt_roundtripWithLargeByteArray_returnsOriginalData() {
        val password = "LargeDataPassword".toCharArray()
        val data = ByteArray(50_000) { (it % 256).toByte() }
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(password, data, salt, iv, encrypt = true)
        val decrypted = pbe.encryptDecrypt(password, encrypted, salt, iv, encrypt = false)

        assertThat(decrypted).isEqualTo(data)
    }

    @Test
    fun decrypt_withIncorrectPassword_throwsGeneralSecurityException() {
        val correctPassword = "CorrectPassword123".toCharArray()
        val wrongPassword = "WrongPassword123".toCharArray()
        val data = "SecretPayload".toByteArray(Charsets.UTF_8)
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(correctPassword, data, salt, iv, encrypt = true)

        assertThrows(GeneralSecurityException::class.java) {
            pbe.encryptDecrypt(wrongPassword, encrypted, salt, iv, encrypt = false)
        }
    }

    @Test
    fun decrypt_withTamperedPaddingBlock_throwsGeneralSecurityException() {
        val password = "PasswordForTamperTest".toCharArray()
        val data = "UncorruptedPlaintextData".toByteArray(Charsets.UTF_8)
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(password, data, salt, iv, encrypt = true)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            pbe.encryptDecrypt(password, encrypted, salt, iv, encrypt = false)
        }
    }

    @Test
    fun decrypt_withTamperedIntermediateBlock_producesCorruptedPlaintext() {
        val password = "PasswordForTamperTest".toCharArray()
        val data = "UncorruptedPlaintextDataLongEnoughForMultipleBlocks".toByteArray(Charsets.UTF_8)
        val salt = pbe.getRandomSalt()
        val iv = pbe.getRandomIV()

        val encrypted = pbe.encryptDecrypt(password, data, salt, iv, encrypt = true)
        encrypted[0] = (encrypted[0].toInt() xor 0xFF).toByte()

        val result =
            runCatching { pbe.encryptDecrypt(password, encrypted, salt, iv, encrypt = false) }
        if (result.isSuccess) {
            assertThat(result.getOrThrow()).isNotEqualTo(data)
        } else {
            assertThat(result.exceptionOrNull()).isInstanceOf(GeneralSecurityException::class.java)
        }
    }

    @Test
    fun decrypt_withInvalidIvSize_throwsInvalidAlgorithmParameterException() {
        val password = "PasswordForIvSizeTest".toCharArray()
        val data = "ValidPayload".toByteArray(Charsets.UTF_8)
        val salt = pbe.getRandomSalt()
        val invalidIv = ByteArray(8) // Invalid IV size (AES requires 16 bytes)

        assertThrows(InvalidAlgorithmParameterException::class.java) {
            pbe.encryptDecrypt(password, data, salt, invalidIv, encrypt = true)
        }
    }

    @Test
    fun verifyPbeConstants_matchSecuritySpecifications() {
        assertThat(PasswordBasedEncryptionImpl.Constants.SALT_SIZE).isEqualTo(256)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_ITERATION_COUNT).isEqualTo(1324)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_LENGTH).isEqualTo(256)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_FACTORY_ALGO).isEqualTo("PBKDF2WithHmacSHA1")
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_ALGO).isEqualTo("AES")
        assertThat(PasswordBasedEncryptionImpl.Constants.IV_SIZE).isEqualTo(16)
        assertThat(PasswordBasedEncryptionImpl.Constants.CIPHER_TRANSFORMATION).isEqualTo("AES/CBC/PKCS5Padding")
    }
}
