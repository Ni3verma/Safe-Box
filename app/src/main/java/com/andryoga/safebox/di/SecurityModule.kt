package com.andryoga.safebox.di

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.security.DecryptFailureReportingSymmetricKeyUtils
import com.andryoga.safebox.security.HashingUtilsImpl
import com.andryoga.safebox.security.PasswordBasedEncryptionImpl
import com.andryoga.safebox.security.SymmetricKeyUtilsImpl
import com.andryoga.safebox.security.envelope.EnvelopeEncryptionManagerImpl
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.interfaces.EnvelopeEncryptionManager
import com.andryoga.safebox.security.interfaces.HashingUtils
import com.andryoga.safebox.security.interfaces.PasswordBasedEncryption
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the app's cryptography.
 *
 * There is no ambient field-encryption key any more: [SymmetricKeyUtils] reads the session key from
 * [SessionKeyHolder] on every call, and the legacy `symmetricDataKey` alias is only ever read
 * through `KeystoreGateway.legacyVaultKey()`, never generated here.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Singleton
    @Provides
    fun provideSymmetricKeyUtils(
        sessionKeyHolder: SessionKeyHolder,
        analyticsHelper: AnalyticsHelper,
    ): SymmetricKeyUtils {
        return DecryptFailureReportingSymmetricKeyUtils(
            delegate = SymmetricKeyUtilsImpl(sessionKeyHolder),
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

    @Singleton
    @Provides
    fun provideSessionKeyHolder(
        impl: SessionKeyHolderImpl,
    ): SessionKeyHolder = impl

    // TODO(MR5): wired into SignupViewModel, LoginViewModel, LegacyVaultMigrator, and VaultKeyMaintenance
    @Singleton
    @Provides
    fun provideEnvelopeEncryptionManager(
        impl: EnvelopeEncryptionManagerImpl,
    ): EnvelopeEncryptionManager = impl
}
