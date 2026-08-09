package com.andryoga.safebox.di

import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module providing TOTP generator and engine dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object TotpModule {

    @Singleton
    @Provides
    fun provideTotpGenerator(): TotpGenerator {
        return TotpGeneratorImpl()
    }
}
