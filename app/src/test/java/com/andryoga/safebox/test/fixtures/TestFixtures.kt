package com.andryoga.safebox.test.fixtures

import com.andryoga.safebox.data.db.docs.SearchBankAccountData
import com.andryoga.safebox.data.db.docs.SearchBankCardData
import com.andryoga.safebox.data.db.docs.SearchLoginData
import com.andryoga.safebox.data.db.docs.SearchSecureNoteData
import com.andryoga.safebox.data.db.docs.export.ExportBankAccountData
import com.andryoga.safebox.data.db.docs.export.ExportBankCardData
import com.andryoga.safebox.data.db.docs.export.ExportLoginData
import com.andryoga.safebox.data.db.docs.export.ExportSecureNoteData
import com.andryoga.safebox.data.db.entity.BackupMetadataEntity
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
import com.andryoga.safebox.data.db.entity.LoginDataEntity
import com.andryoga.safebox.data.db.entity.SecureNoteDataEntity
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.domain.models.backup.BackupPathData
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import java.util.Date

object TestFixtures {
    val fixedDate: Date = Date(1700000000000L)

    fun createTestUserDetailsEntity(
        key: Int = 1,
        uid: String = "test-uid-123",
        password: String = "hashed_password",
        hint: String? = "encrypted_hint",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
    ) = UserDetailsEntity(
        key = key,
        uid = uid,
        password = password,
        hint = hint,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestLoginData(
        id: Int? = 1,
        title: String = "Test Login",
        url: String? = "https://example.com",
        userId: String = "user@example.com",
        password: String? = "secretPassword",
        notes: String? = "login notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
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

    fun createTestLoginDataEntity(
        key: Int = 1,
        title: String = "Test Login",
        url: String? = "https://example.com",
        userId: String = "user@example.com",
        password: String? = "secretPassword",
        notes: String? = "login notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
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

    fun createTestCardData(
        id: Int? = 1,
        title: String = "Test Card",
        name: String? = "John Doe",
        number: String = "1234567812345678",
        expiryDate: String? = "1228",
        cvv: String? = "123",
        pin: String? = "4321",
        notes: String? = "card notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
    ) = CardData(
        id = id,
        title = title,
        name = name,
        number = number,
        expiryDate = expiryDate,
        cvv = cvv,
        pin = pin,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestBankCardDataEntity(
        key: Int = 1,
        title: String = "Test Card",
        name: String? = "John Doe",
        number: String = "1234567812345678",
        expiryDate: String? = "1228",
        cvv: String? = "123",
        pin: String? = "4321",
        notes: String? = "card notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
    ) = BankCardDataEntity(
        key = key,
        title = title,
        name = name,
        number = number,
        expiryDate = expiryDate,
        cvv = cvv,
        pin = pin,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestBankAccountData(
        id: Int? = 1,
        title: String = "Test Bank",
        accountNo: String = "987654321012",
        customerName: String? = "John Doe",
        customerId: String? = "CUST123",
        branchCode: String? = "BR001",
        branchName: String? = "Main Branch",
        branchAddress: String? = "123 Main St",
        ifscCode: String? = "IFSC0001",
        micrCode: String? = "MICR0001",
        notes: String? = "account notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
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

    fun createTestBankAccountDataEntity(
        key: Int = 1,
        title: String = "Test Bank",
        accountNumber: String = "987654321012",
        customerName: String? = "John Doe",
        customerId: String? = "CUST123",
        branchCode: String? = "BR001",
        branchName: String? = "Main Branch",
        branchAddress: String? = "123 Main St",
        ifscCode: String? = "IFSC0001",
        micrCode: String? = "MICR0001",
        notes: String? = "account notes",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
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

    fun createTestNoteData(
        id: Int? = 1,
        title: String = "Test Note",
        notes: String = "Secret Note Content",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
    ) = NoteData(
        id = id,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestSecureNoteDataEntity(
        key: Int = 1,
        title: String = "Test Note",
        notes: String = "Secret Note Content",
        creationDate: Date = fixedDate,
        updateDate: Date = fixedDate
    ) = SecureNoteDataEntity(
        key = key,
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestBackupMetadataEntity(
        key: Int = 1,
        uriString: String = "content://com.android.providers.downloads.documents/tree/downloads",
        displayPath: String = "/storage/emulated/0/Download",
        lastBackupDate: Date? = fixedDate,
        createdOn: Date = fixedDate
    ) = BackupMetadataEntity(
        key = key,
        uriString = uriString,
        displayPath = displayPath,
        lastBackupDate = lastBackupDate,
        createdOn = createdOn
    )

    fun createTestBackupPathData(
        uriString: String = "content://com.android.providers.downloads.documents/tree/downloads",
        path: String = "/storage/emulated/0/Download",
        lastBackupTime: String = "Sunday, 15 Nov 2023 10:00 AM"
    ) = BackupPathData(
        uriString = uriString,
        path = path,
        lastBackupTime = lastBackupTime
    )

    fun createTestSearchLoginData(
        key: Int = 1,
        title: String = "Test Login",
        userId: String = "user@example.com",
        creationDate: Date = fixedDate
    ) = SearchLoginData(
        key = key,
        title = title,
        userId = userId,
        creationDate = creationDate
    )

    fun createTestSearchBankCardData(
        key: Int = 1,
        title: String = "Test Card",
        number: String = "1234567812345678",
        creationDate: Date = fixedDate
    ) = SearchBankCardData(
        key = key,
        title = title,
        number = number,
        creationDate = creationDate
    )

    fun createTestSearchBankAccountData(
        key: Int = 1,
        title: String = "Test Bank",
        accountNumber: String = "987654321012",
        creationDate: Date = fixedDate
    ) = SearchBankAccountData(
        key = key,
        title = title,
        accountNumber = accountNumber,
        creationDate = creationDate
    )

    fun createTestSearchSecureNoteData(
        key: Int = 1,
        title: String = "Test Note",
        creationDate: Date = fixedDate
    ) = SearchSecureNoteData(
        key = key,
        title = title,
        creationDate = creationDate
    )

    val fixedDateLong: Long = 1700000000000L

    fun createTestExportLoginData(
        title: String = "Test Login",
        url: String? = "https://example.com",
        password: String? = "secretPassword",
        notes: String? = "login notes",
        userId: String = "user@example.com",
        creationDate: Long = fixedDateLong,
        updateDate: Long = fixedDateLong
    ) = ExportLoginData(
        title = title,
        url = url,
        password = password,
        notes = notes,
        userId = userId,
        creationDate = creationDate,
        updateDate = updateDate
    )

    fun createTestExportBankCardData(
        title: String = "Test Card",
        name: String? = "John Doe",
        number: String = "1234567812345678",
        pin: String? = "4321",
        cvv: String? = "123",
        expiryDate: String? = "1228",
        notes: String? = "card notes",
        creationDate: Long = fixedDateLong,
        updateDate: Long = fixedDateLong
    ) = ExportBankCardData(
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

    fun createTestExportBankAccountData(
        title: String = "Test Bank",
        accountNumber: String = "987654321012",
        customerName: String? = "John Doe",
        customerId: String? = "CUST123",
        branchCode: String? = "BR001",
        branchName: String? = "Main Branch",
        branchAddress: String? = "123 Main St",
        ifscCode: String? = "IFSC0001",
        micrCode: String? = "MICR0001",
        notes: String? = "account notes",
        creationDate: Long = fixedDateLong,
        updateDate: Long = fixedDateLong
    ) = ExportBankAccountData(
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

    fun createTestExportSecureNoteData(
        title: String = "Test Note",
        notes: String = "Secret Note Content",
        creationDate: Long = fixedDateLong,
        updateDate: Long = fixedDateLong
    ) = ExportSecureNoteData(
        title = title,
        notes = notes,
        creationDate = creationDate,
        updateDate = updateDate
    )
}
