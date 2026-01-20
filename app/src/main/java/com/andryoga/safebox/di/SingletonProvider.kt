package com.andryoga.safebox.di

import android.content.Context
import androidx.work.WorkManager
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.FirebaseAnalytics
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SingletonProvider {
    @Singleton
    @Provides
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Singleton
    @Provides
    fun provideReviewManager(@ApplicationContext context: Context): ReviewManager {
        return ReviewManagerFactory.create(context)
    }

    @Provides
    @IsDebug
    fun provideIsDebug(): Boolean = BuildConfig.DEBUG

    @Provides
    @Singleton
    fun provideAnalyticsHelper(): AnalyticsHelper {
        return FirebaseAnalytics()
    }
}
