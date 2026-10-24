package com.andryoga.safebox.di

import com.andryoga.safebox.security.envelope.AndroidKeystoreGateway
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the `AndroidKeyStore` seam on its own so `androidTest` can swap it for the JCE-backed
 * fake (`TestKeystoreModule`) while every binding in [SecurityModule] stays real.
 */
@Module
@InstallIn(SingletonComponent::class)
object KeystoreModule {

    // TODO(MR5): wired into EnvelopeEncryptionManagerImpl, LegacyVaultMigrator, and VaultKeyMaintenance
    @Singleton
    @Provides
    fun provideKeystoreGateway(
        impl: AndroidKeystoreGateway,
    ): KeystoreGateway = impl
}
