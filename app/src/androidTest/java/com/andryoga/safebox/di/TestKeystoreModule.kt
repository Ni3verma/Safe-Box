package com.andryoga.safebox.di

import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [KeystoreModule] in instrumentation tests with the JCE-backed [FakeKeystoreGateway], so
 * suites run on images without a secure lock screen (the CI `aosp-atd` image, ADR-0011 S8).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [KeystoreModule::class],
)
abstract class TestKeystoreModule {
    @Binds
    @Singleton
    abstract fun bindKeystoreGateway(
        fake: FakeKeystoreGateway,
    ): KeystoreGateway
}
