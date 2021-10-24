package com.andryoga.safebox.di

import android.content.Context
import androidx.room.Room
import com.andryoga.safebox.data.db.MIGRATION_1_2
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.*
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
        ).addMigrations(MIGRATION_1_2).build()
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

    @Singleton
    @Provides
    fun provideBankAccountDataDao(
        safeBoxDatabase: SafeBoxDatabase
    ): BankAccountDataDao {
        return safeBoxDatabase.bankAccountDataDao()
    }

    @Singleton
    @Provides
    fun provideBankCardDataDao(
        safeBoxDatabase: SafeBoxDatabase
    ): BankCardDataDao {
        return safeBoxDatabase.bankCardDataDao()
    }

    @Singleton
    @Provides
    fun provideSecureNoteDataDao(
        safeBoxDatabase: SafeBoxDatabase
    ): SecureNoteDataDao {
        return safeBoxDatabase.secureNoteDataDao()
    }
}
