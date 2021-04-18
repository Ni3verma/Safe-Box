package com.andryoga.safebox.security

import com.andryoga.safebox.security.interfaces.HashingUtils
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class HashingUtilsImpl : HashingUtils {
    private val separator = "|"
    private val secureRandomAlgo = "SHA1PRNG"
    private val hashingAlgo = "PBKDF2WithHmacSHA1"
    private val iterationCount = 500
    private val keyLength = 64 * 8

    private val secureRandom: SecureRandom = SecureRandom.getInstance(secureRandomAlgo)
    private val keyFactory = SecretKeyFactory.getInstance(hashingAlgo)

    override suspend fun hash(password: String): String {
        val salt = getSalt()
        val base64EncodedSalt = SecurityUtils.encodeBase64(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, iterationCount, keyLength)
        val base64EncodedPasswordHash = SecurityUtils.encodeBase64(
            keyFactory.generateSecret(spec).encoded
        )

        return base64EncodedPasswordHash + separator + base64EncodedSalt
    }

    override suspend fun compareHash(toCompareText: String, toCompareWithHash: String): Boolean {
        val hashInfo = toCompareWithHash.split(separator)
        if (hashInfo.size != 2) {
            throw SecurityException("proper hash info not passed. It must be of this format : {base64 encoded hash(password+salt)}+$separator+{base64 encoded salt}")
        }
        val salt = SecurityUtils.decodeBase64(hashInfo[1])

        val spec = PBEKeySpec(toCompareText.toCharArray(), salt, iterationCount, keyLength)
        val base64EncodedPasswordHash = SecurityUtils.encodeBase64(
            keyFactory.generateSecret(spec).encoded
        )

        return base64EncodedPasswordHash == hashInfo[0]
    }

    private fun getSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }
}