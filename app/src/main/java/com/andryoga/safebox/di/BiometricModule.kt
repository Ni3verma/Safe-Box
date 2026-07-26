package com.andryoga.safebox.di

import com.andryoga.safebox.ui.core.BiometricAuthProvider
import com.andryoga.safebox.ui.core.DefaultBiometricAuthProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BiometricModule {
    @Binds
    @Singleton
    abstract fun bindBiometricAuthProvider(
        impl: DefaultBiometricAuthProvider
    ): BiometricAuthProvider
}
