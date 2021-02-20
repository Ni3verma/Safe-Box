package com.andryoga.safebox.di

import com.andryoga.safebox.security.KeyStoreUtils
import com.andryoga.safebox.security.KeyStoreUtilsImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonBindModule {
    @Singleton
    @Binds
    abstract fun bindKeyStoreUtil(
        keyStoreUtilsImpl: KeyStoreUtilsImpl
    ): KeyStoreUtils

}