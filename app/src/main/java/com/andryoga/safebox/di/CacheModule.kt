package com.andryoga.safebox.di

import android.content.Context
import androidx.room.Room
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Singleton
    @Provides
    fun provideSafeBoxAppDb(
        @ApplicationContext context: Context
    ): SafeBoxDatabase {
        return Room.databaseBuilder(
            context,
            SafeBoxDatabase::class.java,
            SafeBoxDatabase.DATABASE_NAME
        ).build()
    }

    // DAO
    @Singleton
    @Provides
    fun provideLoginDataDao(
        safeBoxDatabase: SafeBoxDatabase
    ): LoginDataDao {
        return safeBoxDatabase.loginDataDao()
    }

    @Singleton
    @Provides
    fun provideUserDetailsDao(
        safeBoxDatabase: SafeBoxDatabase
    ): UserDetailsDao {
        return safeBoxDatabase.userDetailsDao()
    }

    // Secure DAO
}
