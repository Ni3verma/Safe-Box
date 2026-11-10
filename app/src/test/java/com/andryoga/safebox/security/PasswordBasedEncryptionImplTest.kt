package com.andryoga.safebox.security

import com.andryoga.safebox.test.fixtures.LegacyBackupCipher
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException

/**
 * Inputs are the fixed salt and IV of [LegacyBackupCipher]: AES-CBC has no MAC, so a wrong key
 * fails only when the scrambled last block is not valid padding, and fixed inputs make each
 * outcome below a known answer rather than a probability.
 */
class PasswordBasedEncryptionImplTest {

    private val pbe = PasswordBasedEncryptionImpl()

    @Test
    fun decrypt_payloadEncryptedWithTheLegacyScheme_returnsOriginalData() {
        val password = "StrongMasterPassword123".toCharArray()
        val data = "SensitiveDatabasePayloadToEncrypt".toByteArray(Charsets.UTF_8)
        val encrypted = LegacyBackupCipher.encrypt(password, data)

        val decrypted = pbe.decrypt(password, encrypted, LegacyBackupCipher.FIXED_SALT, LegacyBackupCipher.FIXED_IV)

        assertThat(decrypted).isEqualTo(data)
    }

    @Test
    fun decrypt_largePayload_returnsOriginalData() {
        val password = "LargeDataPassword".toCharArray()
        val data = ByteArray(50_000) { (it % 256).toByte() }
        val encrypted = LegacyBackupCipher.encrypt(password, data)

        val decrypted = pbe.decrypt(password, encrypted, LegacyBackupCipher.FIXED_SALT, LegacyBackupCipher.FIXED_IV)

        assertThat(decrypted).isEqualTo(data)
    }

    @Test
    fun decrypt_doesNotModifyThePassword() {
        val password = "KeepMe123".toCharArray()
        val encrypted = LegacyBackupCipher.encrypt(password.copyOf(), "x".toByteArray())

        pbe.decrypt(password, encrypted, LegacyBackupCipher.FIXED_SALT, LegacyBackupCipher.FIXED_IV)

        assertThat(String(password)).isEqualTo("KeepMe123")
    }

    @Test
    fun decrypt_withIncorrectPassword_throwsGeneralSecurityException() {
        val encrypted = LegacyBackupCipher.encrypt("CorrectPassword123".toCharArray(), "SecretPayload".toByteArray())

        assertThrows(GeneralSecurityException::class.java) {
            pbe.decrypt(
                "WrongPassword123".toCharArray(),
                encrypted,
                LegacyBackupCipher.FIXED_SALT,
                LegacyBackupCipher.FIXED_IV,
            )
        }
    }

    @Test
    fun decrypt_withTamperedPaddingBlock_throwsGeneralSecurityException() {
        val password = "PasswordForTamperTest".toCharArray()
        val encrypted = LegacyBackupCipher.encrypt(password, "UncorruptedPlaintextData".toByteArray())
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            pbe.decrypt(password, encrypted, LegacyBackupCipher.FIXED_SALT, LegacyBackupCipher.FIXED_IV)
        }
    }

    @Test
    fun decrypt_withInvalidIvSize_throwsInvalidAlgorithmParameterException() {
        val invalidIv = ByteArray(8) // AES-CBC needs 16 bytes

        assertThrows(InvalidAlgorithmParameterException::class.java) {
            pbe.decrypt("PasswordForIvSizeTest".toCharArray(), ByteArray(16), LegacyBackupCipher.FIXED_SALT, invalidIv)
        }
    }

    @Test
    fun verifyPbeConstants_matchTheFrozenLegacyFormat() {
        assertThat(PasswordBasedEncryptionImpl.Constants.SALT_SIZE).isEqualTo(256)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_ITERATION_COUNT).isEqualTo(1324)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_LENGTH).isEqualTo(256)
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_FACTORY_ALGO).isEqualTo("PBKDF2WithHmacSHA1")
        assertThat(PasswordBasedEncryptionImpl.Constants.KEY_ALGO).isEqualTo("AES")
        assertThat(PasswordBasedEncryptionImpl.Constants.IV_SIZE).isEqualTo(16)
        assertThat(PasswordBasedEncryptionImpl.Constants.CIPHER_TRANSFORMATION).isEqualTo("AES/CBC/PKCS5Padding")
    }
}
