package com.andryoga.safebox.worker.backup.legacy

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PasswordBasedEncryptionImpl : PasswordBasedEncryption {
    /** Parameters of backup formats 1–3; frozen, files written with them must stay readable. */
    object Constants {
        const val SALT_SIZE = 256
        const val KEY_ITERATION_COUNT = 1324
        const val KEY_LENGTH = 256
        const val KEY_FACTORY_ALGO = "PBKDF2WithHmacSHA1"
        const val KEY_ALGO = "AES"
        const val IV_SIZE = 16
        const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    }

    override fun decrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
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
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }
}
