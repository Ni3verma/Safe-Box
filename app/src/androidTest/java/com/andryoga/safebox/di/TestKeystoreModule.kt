package com.andryoga.safebox.di

import com.andryoga.safebox.security.interfaces.CrashReporter
import com.andryoga.safebox.security.interfaces.KeystoreGateway
import com.andryoga.safebox.test.fakes.FakeCrashReporter
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [KeystoreModule] in instrumentation tests with [FakeKeystoreGateway] and
 * [FakeCrashReporter].
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

    @Binds
    @Singleton
    abstract fun bindCrashReporter(
        fake: FakeCrashReporter,
    ): CrashReporter
}
