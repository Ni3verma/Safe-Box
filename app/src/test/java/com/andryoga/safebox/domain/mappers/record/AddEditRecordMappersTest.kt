package com.andryoga.safebox.domain.mappers.record

import com.andryoga.safebox.domain.DomainTestFixtures
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Date

class AddEditRecordMappersTest {

    @Test
    fun noteData_toDbEntity_withNullId_mapsToZeroKeyAndPreservesDates() {
        val beforeTime = System.currentTimeMillis() - 1000
        val originalUpdateDate = Date(1680000000000L)
        val noteData = DomainTestFixtures.createNoteData(
            id = null,
            title = "Personal Vault",
            notes = "Confidential personal thoughts",
            creationDate = Date(1670000000000L),
            updateDate = originalUpdateDate
        )

        val entity = noteData.toDbEntity()

        assertThat(entity.key).isEqualTo(0)
        assertThat(entity.title).isEqualTo("Personal Vault")
        assertThat(entity.notes).isEqualTo("Confidential personal thoughts")
        assertThat(entity.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(entity.updateDate).isEqualTo(originalUpdateDate)
    }

    @Test
    fun secureNoteDataEntity_toNoteData_mapsAllFieldsAccurately() {
        val entity = DomainTestFixtures.createSecureNoteDataEntity(
            key = 15,
            title = "Server Configuration",
            notes = "SSH Key: 0x99AABB",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val domain = entity.toNoteData()

        assertThat(domain.id).isEqualTo(15)
        assertThat(domain.title).isEqualTo("Server Configuration")
        assertThat(domain.notes).isEqualTo("SSH Key: 0x99AABB")
        assertThat(domain.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(domain.updateDate).isEqualTo(Date(1680000000000L))
    }

    @Test
    fun loginData_toDbEntity_mapsAllFieldsAndGeneratesUpdateDate() {
        val beforeTime = System.currentTimeMillis()
        val loginData = DomainTestFixtures.createLoginData(
            id = 5,
            title = "GitHub Account",
            url = "https://github.com",
            userId = "octocat",
            password = "SecurePassword@2026",
            notes = "2FA enabled with YubiKey",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val entity = loginData.toDbEntity()

        assertThat(entity.key).isEqualTo(5)
        assertThat(entity.title).isEqualTo("GitHub Account")
        assertThat(entity.url).isEqualTo("https://github.com")
        assertThat(entity.userId).isEqualTo("octocat")
        assertThat(entity.password).isEqualTo("SecurePassword@2026")
        assertThat(entity.notes).isEqualTo("2FA enabled with YubiKey")
        assertThat(entity.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(entity.updateDate.time).isAtLeast(beforeTime)
    }

    @Test
    fun loginDataEntity_toLoginData_mapsAllFieldsAccurately() {
        val entity = DomainTestFixtures.createLoginDataEntity(
            key = 42,
            title = "AWS Console",
            url = "https://aws.amazon.com",
            userId = "admin_user",
            password = "CloudSecretPassword#1",
            notes = "Root account",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val domain = entity.toLoginData()

        assertThat(domain.id).isEqualTo(42)
        assertThat(domain.title).isEqualTo("AWS Console")
        assertThat(domain.url).isEqualTo("https://aws.amazon.com")
        assertThat(domain.userId).isEqualTo("admin_user")
        assertThat(domain.password).isEqualTo("CloudSecretPassword#1")
        assertThat(domain.notes).isEqualTo("Root account")
        assertThat(domain.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(domain.updateDate).isEqualTo(Date(1680000000000L))
    }

    @Test
    fun bankAccountData_toDbEntity_mapsAccountNoToAccountNumber() {
        val beforeTime = System.currentTimeMillis()
        val accountData = DomainTestFixtures.createBankAccountData(
            id = 10,
            title = "Savings Account",
            accountNo = "112233445566",
            customerName = "Jane Doe",
            customerId = "CUST-999",
            branchCode = "BR-77",
            branchName = "North Branch",
            branchAddress = "789 Pine Rd",
            ifscCode = "BANK990011",
            micrCode = "99887766",
            notes = "High yield savings",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val entity = accountData.toDbEntity()

        assertThat(entity.key).isEqualTo(10)
        assertThat(entity.title).isEqualTo("Savings Account")
        assertThat(entity.accountNumber).isEqualTo("112233445566")
        assertThat(entity.customerName).isEqualTo("Jane Doe")
        assertThat(entity.customerId).isEqualTo("CUST-999")
        assertThat(entity.branchCode).isEqualTo("BR-77")
        assertThat(entity.branchName).isEqualTo("North Branch")
        assertThat(entity.branchAddress).isEqualTo("789 Pine Rd")
        assertThat(entity.ifscCode).isEqualTo("BANK990011")
        assertThat(entity.micrCode).isEqualTo("99887766")
        assertThat(entity.notes).isEqualTo("High yield savings")
        assertThat(entity.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(entity.updateDate.time).isAtLeast(beforeTime)
    }

    @Test
    fun bankAccountDataEntity_toBankAccountData_mapsAccountNumberToAccountNo() {
        val entity = DomainTestFixtures.createBankAccountDataEntity(
            key = 18,
            title = "Checking Account",
            accountNumber = "998877665544",
            customerName = "Alice Smith",
            customerId = "CUST-555",
            branchCode = "BR-12",
            branchName = "South Branch",
            branchAddress = "456 Elm St",
            ifscCode = "BANK123456",
            micrCode = "11223344",
            notes = "Daily checking",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val domain = entity.toBankAccountData()

        assertThat(domain.id).isEqualTo(18)
        assertThat(domain.title).isEqualTo("Checking Account")
        assertThat(domain.accountNo).isEqualTo("998877665544")
        assertThat(domain.customerName).isEqualTo("Alice Smith")
        assertThat(domain.customerId).isEqualTo("CUST-555")
        assertThat(domain.branchCode).isEqualTo("BR-12")
        assertThat(domain.branchName).isEqualTo("South Branch")
        assertThat(domain.branchAddress).isEqualTo("456 Elm St")
        assertThat(domain.ifscCode).isEqualTo("BANK123456")
        assertThat(domain.micrCode).isEqualTo("11223344")
        assertThat(domain.notes).isEqualTo("Daily checking")
        assertThat(domain.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(domain.updateDate).isEqualTo(Date(1680000000000L))
    }

    @Test
    fun cardData_toDbEntity_mapsAllFieldsAndGeneratesUpdateDate() {
        val beforeTime = System.currentTimeMillis()
        val cardData = DomainTestFixtures.createCardData(
            id = 7,
            title = "Corporate Visa",
            name = "Corporate User",
            number = "4000123456789010",
            pin = "9012",
            cvv = "321",
            expiryDate = "05/30",
            notes = "Business expenses",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val entity = cardData.toDbEntity()

        assertThat(entity.key).isEqualTo(7)
        assertThat(entity.title).isEqualTo("Corporate Visa")
        assertThat(entity.name).isEqualTo("Corporate User")
        assertThat(entity.number).isEqualTo("4000123456789010")
        assertThat(entity.pin).isEqualTo("9012")
        assertThat(entity.cvv).isEqualTo("321")
        assertThat(entity.expiryDate).isEqualTo("05/30")
        assertThat(entity.notes).isEqualTo("Business expenses")
        assertThat(entity.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(entity.updateDate.time).isAtLeast(beforeTime)
    }

    @Test
    fun bankCardDataEntity_toCardData_mapsAllFieldsAccurately() {
        val entity = DomainTestFixtures.createBankCardDataEntity(
            key = 88,
            title = "Mastercard Gold",
            name = "Bob Builder",
            number = "5100112233445566",
            pin = "1111",
            cvv = "555",
            expiryDate = "10/27",
            notes = "Emergency backup card",
            creationDate = Date(1670000000000L),
            updateDate = Date(1680000000000L)
        )

        val domain = entity.toCardData()

        assertThat(domain.id).isEqualTo(88)
        assertThat(domain.title).isEqualTo("Mastercard Gold")
        assertThat(domain.name).isEqualTo("Bob Builder")
        assertThat(domain.number).isEqualTo("5100112233445566")
        assertThat(domain.pin).isEqualTo("1111")
        assertThat(domain.cvv).isEqualTo("555")
        assertThat(domain.expiryDate).isEqualTo("10/27")
        assertThat(domain.notes).isEqualTo("Emergency backup card")
        assertThat(domain.creationDate).isEqualTo(Date(1670000000000L))
        assertThat(domain.updateDate).isEqualTo(Date(1680000000000L))
    }
}
