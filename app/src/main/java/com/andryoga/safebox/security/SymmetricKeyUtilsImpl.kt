package com.andryoga.safebox.security

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class SymmetricKeyUtilsImpl
@Inject constructor(
    private val secretKey: SecretKey
) : SymmetricKeyUtils {
    private val separator = "|"

    override fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ivBytes = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())

        val base64EncodedData = SecurityUtils.encodeBase64(encryptedBytes)
        val base64EncodedIvData = SecurityUtils.encodeBase64(ivBytes)

        return base64EncodedData + separator + base64EncodedIvData
    }

    override fun decrypt(data: String): String {
        val dataArray = data.split(separator, limit = 2)
        val base64EncodedEncryptedString = dataArray[0]
        val base64EncodedInitVector = dataArray[1]

        val encryptedByteArray = SecurityUtils.decodeBase64(base64EncodedEncryptedString)
        val initVectorByteArray = SecurityUtils.decodeBase64(base64EncodedInitVector)

        val spec = GCMParameterSpec(128, initVectorByteArray)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(encryptedByteArray)
        return decryptedBytes.toString(Charsets.UTF_8)
    }
}