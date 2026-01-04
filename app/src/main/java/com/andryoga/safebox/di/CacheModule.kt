package com.andryoga.safebox.di

import android.content.Context
import androidx.room.Room
import com.andryoga.safebox.data.db.Migration
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.dao.SecureNoteDataDao
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
            SafeBoxDatabase.Companion.DATABASE_NAME
        ).addMigrations(
            Migration.MIGRATION_1_2,
            Migration.MIGRATION_2_3,
            Migration.MIGRATION_3_4,
            Migration.MIGRATION_4_5
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

    @Singleton
    @Provides
    fun provideBackupMetadataDao(
        safeBoxDatabase: SafeBoxDatabase
    ): BackupMetadataDao {
        return safeBoxDatabase.backupMetadataDao()
    }
}
