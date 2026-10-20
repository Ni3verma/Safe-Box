package com.andryoga.safebox.di

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.security.DecryptFailureReportingSymmetricKeyUtils
import com.andryoga.safebox.security.HashingUtilsImpl
import com.andryoga.safebox.security.PasswordBasedEncryptionImpl
import com.andryoga.safebox.security.SymmetricKeyUtilsImpl
import com.andryoga.safebox.security.interfaces.HashingUtils
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext context: Context,
        analyticsHelper: AnalyticsHelper,
    ): SecretKey {
        return getSymmetricKey(
            onKeyGenerated = {
                logKeystoreKeyCreated(
                    analyticsHelper = analyticsHelper,
                    dbExists = context.getDatabasePath(SafeBoxDatabase.DATABASE_NAME).exists(),
                )
            },
        )
    }

    @Singleton
    @Provides
    fun provideSymmetricKeyUtils(
        secretKey: SecretKey,
        analyticsHelper: AnalyticsHelper,
    ): SymmetricKeyUtils {
        return DecryptFailureReportingSymmetricKeyUtils(
            delegate = SymmetricKeyUtilsImpl(secretKey),
            analyticsHelper = analyticsHelper,
        )
    }

    @Singleton
    @Provides
    fun provideHashingUtils(): HashingUtils {
        return HashingUtilsImpl()
    }

    @Singleton
    @Provides
    fun providePasswordBasedEncryption(): PasswordBasedEncryption {
        return PasswordBasedEncryptionImpl()
    }

    /**
     * @param onKeyGenerated Called only when the alias was absent and a key had to be generated,
     * before the generation itself.
     */
    private fun getSymmetricKey(onKeyGenerated: () -> Unit): SecretKey {
        val alias = "symmetricDataKey"
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(alias)) {
            try {
                onKeyGenerated()
            } catch (_: Exception) {
                // telemetry only: a failing hook must not stop the key from being generated
            }
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

/**
 * Records that the Keystore alias was absent and a key is being generated.
 *
 * @param dbExists whether the Room database file already existed. `true` is the signature of the
 * silent-data-loss case: a vault exists but the key that encrypted it is gone, and the fresh key
 * cannot read it. `false` is an ordinary first launch. Room creates the file on its first query,
 * not at startup, and the key is requested when the first `*DaoSecure` is constructed, so on a
 * first launch this is read before the file exists (verified on a fresh install, 2026-10-20).
 */
// TODO(MR3): delete with the regenerate branch in SecurityModule (debt D12).
internal fun logKeystoreKeyCreated(analyticsHelper: AnalyticsHelper, dbExists: Boolean) {
    analyticsHelper.logEvent(AnalyticsKey.KEYSTORE_KEY_CREATED) {
        param(AnalyticsParam.DB_EXISTS, dbExists)
    }
}
