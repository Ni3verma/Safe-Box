package com.andryoga.composeapp.di

import android.content.Context
import com.andryoga.composeapp.data.db.dao.BackupMetadataDao
import com.andryoga.composeapp.data.db.secureDao.BankAccountDataDaoSecure
import com.andryoga.composeapp.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.composeapp.data.db.secureDao.LoginDataDaoSecure
import com.andryoga.composeapp.data.db.secureDao.SecureNoteDataDaoSecure
import com.andryoga.composeapp.data.db.secureDao.UserDetailsDaoSecure
import com.andryoga.composeapp.data.repository.BackupMetadataRepositoryImpl
import com.andryoga.composeapp.data.repository.BankAccountDataRepositoryImpl
import com.andryoga.composeapp.data.repository.BankCardDataRepositoryImpl
import com.andryoga.composeapp.data.repository.LoginDataRepositoryImpl
import com.andryoga.composeapp.data.repository.SecureNoteDataRepositoryImpl
import com.andryoga.composeapp.data.repository.UserDetailsRepositoryImpl
import com.andryoga.composeapp.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.composeapp.data.repository.interfaces.BankCardDataRepository
import com.andryoga.composeapp.data.repository.interfaces.LoginDataRepository
import com.andryoga.composeapp.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.composeapp.data.repository.interfaces.UserDetailsRepository
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
        userDetailsDaoSecure: UserDetailsDaoSecure
    ): UserDetailsRepository {
        return UserDetailsRepositoryImpl(
            userDetailsDaoSecure
        )
    }

    @ExperimentalCoroutinesApi
    @Singleton
    @Provides
    fun provideLoginDataRepo(
        loginDataDaoSecure: LoginDataDaoSecure
    ): LoginDataRepository {
        return LoginDataRepositoryImpl(loginDataDaoSecure)
    }

    @Singleton
    @Provides
    fun provideBankAccountDataRepo(
        bankAccountDataDaoSecure: BankAccountDataDaoSecure
    ): BankAccountDataRepository {
        return BankAccountDataRepositoryImpl(bankAccountDataDaoSecure)
    }

    @Singleton
    @Provides
    fun provideBankCardDataRepo(
        bankCardDataDaoSecure: BankCardDataDaoSecure
    ): BankCardDataRepository {
        return BankCardDataRepositoryImpl(bankCardDataDaoSecure)
    }

    @Singleton
    @Provides
    fun provideSecureNoteDataRepo(
        secureNoteDataDaoSecure: SecureNoteDataDaoSecure
    ): SecureNoteDataRepository {
        return SecureNoteDataRepositoryImpl(secureNoteDataDaoSecure)
    }

    @Singleton
    @Provides
    fun provideBackupMetadataRepo(
        @ApplicationContext context: Context,
        backupMetadataDao: BackupMetadataDao
    ): BackupMetadataRepository {
        return BackupMetadataRepositoryImpl(context, backupMetadataDao)
    }
}
