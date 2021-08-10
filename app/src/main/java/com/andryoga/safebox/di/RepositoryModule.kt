package com.andryoga.safebox.di

import com.andryoga.safebox.data.db.secureDao.*
import com.andryoga.safebox.data.repository.*
import com.andryoga.safebox.data.repository.interfaces.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}
