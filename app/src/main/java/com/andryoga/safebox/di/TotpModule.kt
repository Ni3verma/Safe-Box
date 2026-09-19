package com.andryoga.safebox.di

import com.andryoga.safebox.totp.engine.TotpGeneratorImpl
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt dependency injection module providing TOTP generator and engine dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TotpModule {

    /**
     * Binds [TotpGeneratorImpl] to [TotpGenerator] interface.
     *
     * @param totpGeneratorImpl Concrete TOTP generator implementation.
     * @return [TotpGenerator] interface bound to the singleton component.
     */
    @Singleton
    @Binds
    abstract fun bindTotpGenerator(
        totpGeneratorImpl: TotpGeneratorImpl,
    ): TotpGenerator
}
