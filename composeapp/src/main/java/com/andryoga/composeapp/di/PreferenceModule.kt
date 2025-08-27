package com.andryoga.composeapp.di

import com.andryoga.composeapp.providers.EncryptedPreferenceProviderImpl
import com.andryoga.composeapp.providers.PreferenceProviderImpl
import com.andryoga.composeapp.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.composeapp.providers.interfaces.PreferenceProvider
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
    abstract fun bindPreferenceProvider(
        preferenceProviderImpl: PreferenceProviderImpl
    ): PreferenceProvider

    @Singleton
    @Binds
    abstract fun bindEncryptedPreferenceProvider(
        encryptedPreferenceProviderImpl: EncryptedPreferenceProviderImpl
    ): EncryptedPreferenceProvider
}
