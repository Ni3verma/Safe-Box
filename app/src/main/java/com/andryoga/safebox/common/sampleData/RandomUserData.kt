package com.andryoga.safebox.common.sampleData

import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import timber.log.Timber
import java.util.Date


object RandomUserData {

    private const val numberOfEachRecord = 50
    private const val number20 = 20
    private const val number200 = 200
    private const val number30 = 30
    private const val number5 = 5
    private const val number3 = 3

    private suspend fun insertFakeLoginData(loginDataRepository: LoginDataRepository) {
        loginDataRepository.upsertLoginData(
            LoginData(
                0,
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number200),
                Date(),
                Date(),
            )
        )
    }

    private suspend fun insertFakeBankAccountData(
        bankAccountDataRepository: BankAccountDataRepository
    ) {
        bankAccountDataRepository.upsertBankAccountData(
            BankAccountData(
                0,
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestInt(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestInt(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number30),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestInt(2, number5),
                RandomTestData.getRandomTestString(2, number200),
                Date(),
                Date(),
            )
        )
    }

    private suspend fun insertFakeBankCardData(bankCardDataRepository: BankCardDataRepository) {
        bankCardDataRepository.upsertBankCardData(
            CardData(
                0,
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestInt(2, number20),
                "02/22",
                RandomTestData.getRandomTestInt(2, number5),
                RandomTestData.getRandomTestInt(2, number3),
                RandomTestData.getRandomTestString(2, number200),
                Date(),
                Date(),
            )
        )
    }

    private suspend fun insertFakeSecureNoteData(secureNoteDataRepository: SecureNoteDataRepository) {
        secureNoteDataRepository.upsertSecureNoteData(
            NoteData(
                0,
                RandomTestData.getRandomTestString(2, number20),
                RandomTestData.getRandomTestString(2, number200),
                Date(),
                Date(),
            )
        )
    }

    suspend fun insertRandomData(
        loginDataRepository: LoginDataRepository,
        bankAccountDataRepository: BankAccountDataRepository,
        bankCardDataRepository: BankCardDataRepository,
        secureNoteDataRepository: SecureNoteDataRepository
    ) {
        for (i in 1..numberOfEachRecord) {
            Timber.i("inserting $i fake record")
            insertFakeLoginData(loginDataRepository)
            insertFakeBankAccountData(bankAccountDataRepository)
            insertFakeBankCardData(bankCardDataRepository)
            insertFakeSecureNoteData(secureNoteDataRepository)
        }
    }
}
