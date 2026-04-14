package com.andryoga.safebox.di

import android.content.Context
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.dao.BackupMetadataDao
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.safebox.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.safebox.data.repository.BackupMetadataRepositoryImpl
import com.andryoga.safebox.data.repository.BankAccountDataRepositoryImpl
import com.andryoga.safebox.data.repository.BankCardDataRepositoryImpl
import com.andryoga.safebox.data.repository.LoginDataRepositoryImpl
import com.andryoga.safebox.data.repository.SecureNoteDataRepositoryImpl
import com.andryoga.safebox.data.repository.UserDetailsRepositoryImpl
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Singleton
    @Provides
    fun provideUserDetailsRepo(
        userDetailsDaoSecure: UserDetailsDaoSecure,
        preferenceProvider: PreferenceProvider,
        settingsDataStore: SettingsDataStore,
    ): UserDetailsRepository {
        return UserDetailsRepositoryImpl(
            userDetailsDaoSecure,
            preferenceProvider,
            settingsDataStore
        )
    }

    @ExperimentalCoroutinesApi
    @Singleton
    @Provides
    fun provideLoginDataRepo(
        loginDataDaoSecure: LoginDataDaoSecure,
        analyticsHelper: AnalyticsHelper
    ): LoginDataRepository {
        return LoginDataRepositoryImpl(loginDataDaoSecure, analyticsHelper)
    }

    @Singleton
    @Provides
    fun provideBankAccountDataRepo(
        bankAccountDataDaoSecure: BankAccountDataDaoSecure,
        analyticsHelper: AnalyticsHelper
    ): BankAccountDataRepository {
        return BankAccountDataRepositoryImpl(bankAccountDataDaoSecure, analyticsHelper)
    }

    @Singleton
    @Provides
    fun provideBankCardDataRepo(
        bankCardDataDaoSecure: BankCardDataDaoSecure,
        analyticsHelper: AnalyticsHelper
    ): BankCardDataRepository {
        return BankCardDataRepositoryImpl(bankCardDataDaoSecure, analyticsHelper)
    }

    @Singleton
    @Provides
    fun provideSecureNoteDataRepo(
        secureNoteDataDaoSecure: SecureNoteDataDaoSecure,
        analyticsHelper: AnalyticsHelper
    ): SecureNoteDataRepository {
        return SecureNoteDataRepositoryImpl(secureNoteDataDaoSecure, analyticsHelper)
    }

    @Singleton
    @Provides
    fun provideBackupMetadataRepo(
        @ApplicationContext context: Context,
        backupMetadataDao: BackupMetadataDao,
        analyticsHelper: AnalyticsHelper
    ): BackupMetadataRepository {
        return BackupMetadataRepositoryImpl(context, backupMetadataDao, analyticsHelper)
    }
}