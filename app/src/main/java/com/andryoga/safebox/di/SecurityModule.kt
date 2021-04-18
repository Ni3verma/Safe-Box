package com.andryoga.safebox.di

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.andryoga.safebox.security.SymmetricKeyUtils
import com.andryoga.safebox.security.SymmetricKeyUtilsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Singleton
    @Provides
    fun provideSymmetricKey(
    ): SecretKey {
        return getSymmetricKey()
    }

    @Singleton
    @Provides
    fun provideSymmetricKeyUtils(
        secretKey: SecretKey
    ): SymmetricKeyUtils {
        return SymmetricKeyUtilsImpl(secretKey)
    }

    private fun getSymmetricKey(): SecretKey {
        val alias = "symmetricDataKey"
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(alias)) {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
//            .setUserAuthenticationRequired(true) //  requires lock screen, invalidated if lock screen is disabled
                .setRandomizedEncryptionRequired(true) //  different ciphertext for same plaintext on each call
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
        val secretKeyEntry =
            keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry

        return secretKeyEntry.secretKey
    }
}