package com.andryoga.safebox.security

import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PasswordBasedEncryptionImpl : PasswordBasedEncryption {
    object Constants {
        const val SALT_SIZE = 256
        const val KEY_ITERATION_COUNT = 1324
        const val KEY_LENGTH = 256
        const val KEY_FACTORY_ALGO = "PBKDF2WithHmacSHA1"
        const val KEY_ALGO = "AES"
        const val IV_SIZE = 16
        const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    }

    override fun encryptDecrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
        encrypt: Boolean
    ): ByteArray {
        val pbKeySpec = PBEKeySpec(
            password, salt,
            Constants.KEY_ITERATION_COUNT,
            Constants.KEY_LENGTH
        )
        val secretKeyFactory = SecretKeyFactory.getInstance(Constants.KEY_FACTORY_ALGO)
        val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
        val keySpec = SecretKeySpec(keyBytes, Constants.KEY_ALGO)

        val ivSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(Constants.CIPHER_TRANSFORMATION)
        cipher.init(
            if (encrypt) {
                Cipher.ENCRYPT_MODE
            } else {
                Cipher.DECRYPT_MODE
            },
            keySpec,
            ivSpec
        )
        return cipher.doFinal(data)
    }

    override fun getRandomSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(Constants.SALT_SIZE)
        random.nextBytes(salt)

        return salt
    }

    override fun getRandomIV(): ByteArray {
        val ivRandom = SecureRandom()
        val iv = ByteArray(Constants.IV_SIZE)
        ivRandom.nextBytes(iv)

        return iv
    }
}
