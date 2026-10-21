package com.andryoga.safebox.di

import android.content.Context
import com.andryoga.safebox.common.AndroidVaultStorageStats
import com.andryoga.safebox.common.VaultStorageStats
import com.andryoga.safebox.security.AndroidDeviceSecurityStatus
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Read-only device and storage facts that exist for analytics.
 *
 * Kept apart from [DeviceSecurityModule] on purpose: androidTest replaces that module with a fake
 * prompt, and these reads are harmless on a real or virtual device, so they stay real in every
 * build.
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {

    @Singleton
    @Provides
    fun provideDeviceSecurityStatus(@ApplicationContext context: Context): DeviceSecurityStatus =
        AndroidDeviceSecurityStatus(context)

    @Singleton
    @Provides
    fun provideVaultStorageStats(@ApplicationContext context: Context): VaultStorageStats =
        AndroidVaultStorageStats(context)
}
