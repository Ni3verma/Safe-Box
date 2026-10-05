package com.andryoga.safebox.di

import android.content.Context
import com.andryoga.safebox.ui.core.appupdate.controller.AppUpdateController
import com.andryoga.safebox.ui.core.appupdate.controller.PlayAppUpdateController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces [AppUpdateModule] for every instrumentation test with Play's [FakeAppUpdateManager],
 * driven through the real [PlayAppUpdateController].
 *
 * This deliberately bypasses `IN_APP_UPDATE_ENABLED`, which is off in `debug`, so the production
 * update code path is exercised. The fake reports no update until a test calls
 * `setUpdateAvailable`, so every other suite runs as if Play had nothing to offer.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppUpdateModule::class],
)
object FakeAppUpdateModule {
    @Provides
    @Singleton
    fun provideFakeAppUpdateManager(@ApplicationContext context: Context): FakeAppUpdateManager =
        FakeAppUpdateManager(context)

    @Provides
    @Singleton
    fun provideAppUpdateManager(fake: FakeAppUpdateManager): AppUpdateManager = fake

    @Provides
    @Singleton
    fun provideAppUpdateController(controller: PlayAppUpdateController): AppUpdateController =
        controller
}
