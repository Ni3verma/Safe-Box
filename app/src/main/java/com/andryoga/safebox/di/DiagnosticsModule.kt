package com.andryoga.safebox.di

import com.andryoga.safebox.common.CrashReporter
import com.andryoga.safebox.common.CrashlyticsCrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the crash-reporting seam on its own so `androidTest` can bind a fake
 * (`TestDiagnosticsModule`) and test runs never post non-fatals to the production Crashlytics project.
 */
@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {

    @Singleton
    @Provides
    fun provideCrashReporter(
        impl: CrashlyticsCrashReporter,
    ): CrashReporter = impl
}
