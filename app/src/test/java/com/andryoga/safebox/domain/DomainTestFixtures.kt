package com.andryoga.safebox.domain

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import java.util.Date

/**
 * Reusable test fixtures and factory methods for Domain record models,
 * database entities, and search document projections.
 */
object DomainTestFixtures {

    val FIXED_CREATION_DATE = Date(1700000000000L)
    val FIXED_UPDATE_DATE = Date(1700050000000L)

    fun createNoteData(
        id: Int? = 1,
        title: String = "Secret Note Title",
        notes: String = "Confidential note content",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = NoteData(
        id = id,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createSecureNoteDataEntity(
        key: Int = 1,
        title: String = "Secret Note Title",
        notes: String = "Confidential note content",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = SecureNoteDataEntity(
        key = key,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createLoginData(
        id: Int? = 2,
        title: String = "Email Account",
        url: String = "https://mail.example.com",
        userId: String = "user@example.com",
        password: String = "SuperSecretPassword123!",
        notes: String = "Personal email notes",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = LoginData(
        id = id,
        title = title,
        url = url,
        userId = userId,
        password = password,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createLoginDataEntity(
        key: Int = 2,
        title: String = "Email Account",
        url: String = "https://mail.example.com",
        userId: String = "user@example.com",
        password: String = "SuperSecretPassword123!",
        notes: String = "Personal email notes",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = LoginDataEntity(
        key = key,
        title = title,
        url = url,
        userId = userId,
        password = password,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createBankAccountData(
        id: Int? = 3,
        title: String = "Primary Checking",
        accountNo: String = "9876543210",
        customerName: String = "John Doe",
        customerId: String = "CUST-001",
        branchCode: String = "BR-101",
        branchName: String = "Downtown Branch",
        branchAddress: String = "123 Main Street",
        ifscCode: String = "BANK0001234",
        micrCode: String = "123456789",
        notes: String = "Salary account",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = BankAccountData(
        id = id,
        title = title,
        accountNo = accountNo,
        customerName = customerName,
        customerId = customerId,
        branchCode = branchCode,
        branchName = branchName,
        branchAddress = branchAddress,
        ifscCode = ifscCode,
        micrCode = micrCode,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createBankAccountDataEntity(
        key: Int = 3,
        title: String = "Primary Checking",
        accountNumber: String = "9876543210",
        customerName: String = "John Doe",
        customerId: String = "CUST-001",
        branchCode: String = "BR-101",
        branchName: String = "Downtown Branch",
        branchAddress: String = "123 Main Street",
        ifscCode: String = "BANK0001234",
        micrCode: String = "123456789",
        notes: String = "Salary account",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = BankAccountDataEntity(
        key = key,
        title = title,
        accountNumber = accountNumber,
        customerName = customerName,
        customerId = customerId,
        branchCode = branchCode,
        branchName = branchName,
        branchAddress = branchAddress,
        ifscCode = ifscCode,
        micrCode = micrCode,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createCardData(
        id: Int? = 4,
        title: String = "Travel Rewards Card",
        name: String = "John Doe",
        number: String = "4111222233334444",
        pin: String = "1234",
        cvv: String = "987",
        expiryDate: String = "12/28",
        notes: String = "Use for flights",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = CardData(
        id = id,
        title = title,
        name = name,
        number = number,
        pin = pin,
        cvv = cvv,
        expiryDate = expiryDate,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createBankCardDataEntity(
        key: Int = 4,
        title: String = "Travel Rewards Card",
        name: String = "John Doe",
        number: String = "4111222233334444",
        pin: String = "1234",
        cvv: String = "987",
        expiryDate: String = "12/28",
        notes: String = "Use for flights",
        creationDate: Date = FIXED_CREATION_DATE,
        updateDate: Date = FIXED_UPDATE_DATE
    ) = BankCardDataEntity(
        key = key,
        title = title,
        name = name,
        number = number,
        pin = pin,
        cvv = cvv,
        expiryDate = expiryDate,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createSearchSecureNoteData(
        key: Int = 10,
        title: String = "Search Note",
        creationDate: Date = FIXED_CREATION_DATE
    ) = SearchSecureNoteData(
        key = key,
        title = title,
        creationDate = creationDate
    )

    fun createSearchBankAccountData(
        key: Int = 20,
        title: String = "Search Account",
        accountNumber: String = "XXXX9999",
        creationDate: Date = FIXED_CREATION_DATE
    ) = SearchBankAccountData(
        key = key,
        title = title,
        accountNumber = accountNumber,
        creationDate = creationDate
    )

    fun createSearchBankCardData(
        key: Int = 30,
        title: String = "Search Card",
        number: String = "XXXX8888",
        creationDate: Date = FIXED_CREATION_DATE
    ) = SearchBankCardData(
        key = key,
        title = title,
        number = number,
        creationDate = creationDate
    )

    fun createSearchLoginData(
        key: Int = 40,
        title: String = "Search Login",
        userId: String = "search_user@test.com",
        creationDate: Date = FIXED_CREATION_DATE
    ) = SearchLoginData(
        key = key,
        title = title,
        userId = userId,
        creationDate = creationDate
    )
}
