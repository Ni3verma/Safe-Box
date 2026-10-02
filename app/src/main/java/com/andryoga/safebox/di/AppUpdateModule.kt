package com.andryoga.safebox.di

import android.content.Context
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.ui.core.appupdate.controller.AppUpdateController
import com.andryoga.safebox.ui.core.appupdate.controller.NoOpAppUpdateController
import com.andryoga.safebox.ui.core.appupdate.controller.PlayAppUpdateController
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the in-app update boundary.
 *
 * It is kept separate from [SingletonProvider] so androidTest can swap the whole module for
 * `FakeAppUpdateModule` with `@TestInstallIn`.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)

    /**
     * Returns the Play-backed controller only when `IN_APP_UPDATE_ENABLED` is on, which is only in
     * `release`. [Lazy] keeps the Play manager from ever being created in other builds.
     */
    @Provides
    @Singleton
    fun provideAppUpdateController(
        playAppUpdateController: Lazy<PlayAppUpdateController>,
    ): AppUpdateController = if (BuildConfig.IN_APP_UPDATE_ENABLED) {
        playAppUpdateController.get()
    } else {
        NoOpAppUpdateController()
    }
}
