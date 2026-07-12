package com.andryoga.safebox

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import dagger.hilt.android.testing.CustomTestApplication

/**
 * Base Application class for Hilt instrumented tests implementing [Configuration.Provider].
 * Prevents "WorkManager is not initialized properly" exceptions during UI testing when
 * WorkManagerInitializer is disabled in AndroidManifest.xml.
 */
open class BaseTestApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}

@CustomTestApplication(BaseTestApplication::class)
interface HiltTestApp
