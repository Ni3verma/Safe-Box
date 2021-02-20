package com.andryoga.safebox.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.andryoga.safebox.common.Constants.Companion.IS_KEY_GENERATED
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreUtilsImpl(
    preferenceProvider: PreferenceProvider
) : KeyStoreUtils {
    private val secretKey: SecretKey
    private val separator = "|"

    init {
        val isKeyAlreadyPresent =
            preferenceProvider.getBooleanPref(IS_KEY_GENERATED, false)
        if (!isKeyAlreadyPresent) {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                "SafeBox",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
//            .setUserAuthenticationRequired(true) //  requires lock screen, invalidated if lock screen is disabled
                .setRandomizedEncryptionRequired(true) //  different ciphertext for same plaintext on each call
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()

            preferenceProvider.upsertBooleanPref(IS_KEY_GENERATED, true)
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val secretKeyEntry =
            keyStore.getEntry("SafeBox", null) as KeyStore.SecretKeyEntry
        secretKey = secretKeyEntry.secretKey
    }

    override fun getSecretKey(): SecretKey {
        return secretKey
    }

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