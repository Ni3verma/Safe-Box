package com.andryoga.safebox.di

import com.andryoga.safebox.common.DefaultDispatchersProvider
import com.andryoga.safebox.common.DispatchersProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindDispatchersProvider(
        dispatchersProvider: DefaultDispatchersProvider
    ): DispatchersProvider
}
