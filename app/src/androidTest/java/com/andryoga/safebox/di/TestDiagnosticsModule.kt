package com.andryoga.safebox.di

import com.andryoga.safebox.common.CrashReporter
import com.andryoga.safebox.test.fakes.FakeCrashReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [DiagnosticsModule] in instrumentation tests with [FakeCrashReporter], so a test run
 * never posts non-fatals to the production Crashlytics project.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DiagnosticsModule::class],
)
abstract class TestDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindCrashReporter(
        fake: FakeCrashReporter,
    ): CrashReporter
}
