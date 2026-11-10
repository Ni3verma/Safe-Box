package com.andryoga.safebox.test.fixtures

import com.andryoga.safebox.worker.backup.legacy.PasswordBasedEncryptionImpl.Constants
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts payloads the way builds before format 4 did, so tests can create format 1–3 files now
 * that the app only decrypts them.
 */
object LegacyBackupCipher {
    /** Salt of the frozen legacy size; fixed so every run produces the same ciphertext. */
    val FIXED_SALT = ByteArray(Constants.SALT_SIZE) { it.toByte() }

    /** IV of the frozen legacy size; fixed for the same reason as [FIXED_SALT]. */
    val FIXED_IV = ByteArray(Constants.IV_SIZE) { it.toByte() }

    /**
     * @return [data] encrypted under PBKDF2-SHA1 + AES-CBC with the legacy parameters.
     */
    fun encrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray = FIXED_SALT,
        iv: ByteArray = FIXED_IV,
    ): ByteArray {
        val keySpec = PBEKeySpec(password, salt, Constants.KEY_ITERATION_COUNT, Constants.KEY_LENGTH)
        val keyBytes = SecretKeyFactory.getInstance(Constants.KEY_FACTORY_ALGO).generateSecret(keySpec).encoded
        val cipher = Cipher.getInstance(Constants.CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, Constants.KEY_ALGO), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}
