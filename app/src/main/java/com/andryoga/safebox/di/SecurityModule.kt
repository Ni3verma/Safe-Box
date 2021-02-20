package com.andryoga.safebox.di

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.andryoga.safebox.common.Constants
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
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
    fun provideSecretKey(
        preferenceProvider: PreferenceProvider
    ): SecretKey {
        val isKeyAlreadyPresent =
            preferenceProvider.getBooleanPref(Constants.IS_KEY_GENERATED, false)
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

            preferenceProvider.upsertBooleanPref(Constants.IS_KEY_GENERATED, true)
        }

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val secretKeyEntry =
            keyStore.getEntry("SafeBox", null) as KeyStore.SecretKeyEntry
        return secretKeyEntry.secretKey
    }
}