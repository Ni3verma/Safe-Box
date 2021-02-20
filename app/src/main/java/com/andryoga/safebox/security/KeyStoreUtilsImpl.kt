package com.andryoga.safebox.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class KeyStoreUtilsImpl
@Inject constructor(
    private val secretKey: SecretKey
) : KeyStoreUtils {
    private val separator = "|"

    override fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ivBytes = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())

        val base64EncodedData = convertByteArrayToBase64EncodedString(encryptedBytes)
        val base64EncodedIvData = convertByteArrayToBase64EncodedString(ivBytes)

        return base64EncodedData + separator + base64EncodedIvData
    }

    override fun decrypt(data: String): String {
        val dataArray = data.split(separator, limit = 2)
        val base64EncodedEncryptedString = dataArray[0]
        val base64EncodedInitVector = dataArray[1]

        val encryptedByteArray = convertBase64EncodedStringToByteArray(base64EncodedEncryptedString)
        val initVectorByteArray = convertBase64EncodedStringToByteArray(base64EncodedInitVector)

        val spec = GCMParameterSpec(128, initVectorByteArray)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(encryptedByteArray)
        return decryptedBytes.toString(Charsets.UTF_8)
    }

    private fun convertByteArrayToBase64EncodedString(byteArray: ByteArray): String {
        val base64EncodedByteArray = Base64.encode(byteArray, Base64.DEFAULT)
        return Base64.encodeToString(base64EncodedByteArray, Base64.DEFAULT)
    }

    private fun convertBase64EncodedStringToByteArray(encodedString: String): ByteArray {
        val base64Byte = Base64.decode(encodedString, Base64.DEFAULT)
        return Base64.decode(base64Byte, Base64.DEFAULT)
    }

}