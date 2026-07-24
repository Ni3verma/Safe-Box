package com.andryoga.safebox

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.CustomTestApplication
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

/**
 * Entry point for resolving [HiltWorkerFactory] when accessing it inside [Configuration.Provider]
 * for Hilt instrumented tests because @CustomTestApplication does not support @Inject fields on superclasses.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun getWorkerFactory(): HiltWorkerFactory
}

/**
 * Base Application class for Hilt instrumented tests implementing [Configuration.Provider].
 * Prevents "WorkManager is not initialized properly" exceptions during UI testing when
 * WorkManagerInitializer is disabled in AndroidManifest.xml, and wires [HiltWorkerFactory]
 * via [WorkerFactoryEntryPoint] so that @HiltWorker/@AssistedInject workers can be cleanly instantiated in E2E tests.
 */
open class BaseTestApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPoints.get(this, WorkerFactoryEntryPoint::class.java).getWorkerFactory()
            )
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

@CustomTestApplication(BaseTestApplication::class)
interface HiltTestApp
