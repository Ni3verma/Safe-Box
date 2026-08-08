package com.andryoga.safebox.di

import com.andryoga.safebox.ui.core.DefaultDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.DeviceSecurityAuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceSecurityModule {
    @Binds
    @Singleton
    abstract fun bindDeviceSecurityAuthProvider(
        impl: DefaultDeviceSecurityAuthProvider
    ): DeviceSecurityAuthProvider
}
