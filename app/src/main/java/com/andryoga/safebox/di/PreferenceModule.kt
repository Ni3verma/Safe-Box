package com.andryoga.safebox.di

import com.andryoga.safebox.providers.EncryptedPreferenceProviderImpl
import com.andryoga.safebox.providers.PreferenceProviderImpl
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferenceModule {
    @Singleton
    @Binds
    abstract fun bindPreferenceProvider(preferenceProviderImpl: PreferenceProviderImpl): PreferenceProvider

    @Singleton
    @Binds
    abstract fun bindEncryptedPreferenceProvider(
        encryptedPreferenceProviderImpl: EncryptedPreferenceProviderImpl,
    ): EncryptedPreferenceProvider
}
