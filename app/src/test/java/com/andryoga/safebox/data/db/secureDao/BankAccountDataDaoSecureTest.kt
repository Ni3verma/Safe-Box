@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.BankAccountDataDao
import com.andryoga.safebox.data.db.entity.BankAccountDataEntity
import com.andryoga.safebox.test.fakes.FakeSymmetricKeyUtils
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BankAccountDataDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var bankAccountDataDao: BankAccountDataDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var bankAccountDataDaoSecure: BankAccountDataDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        bankAccountDataDaoSecure = BankAccountDataDaoSecure(
            bankAccountDataDao = bankAccountDataDao,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    @Test
    fun upsertBankAccountData_shouldEncryptSensitiveFieldsBeforePassingToDao() = runTest {
        val inputEntity = TestFixtures.createTestBankAccountDataEntity(
            title = "Checking Account",
            accountNumber = "987654321012",
            customerName = "Jane Doe",
            customerId = "CUST999",
            ifscCode = "IFSC1234",
            micrCode = "MICR5678",
            notes = "salary credit"
        )
        val slot = slot<BankAccountDataEntity>()
        coEvery { bankAccountDataDao.upsertBankAccountData(capture(slot)) } returns Unit

        bankAccountDataDaoSecure.upsertBankAccountData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.title).isEqualTo("Checking Account")
        assertThat(slot.captured.accountNumber).isEqualTo("ENC[987654321012]")
        assertThat(slot.captured.customerName).isEqualTo("Jane Doe")
        assertThat(slot.captured.customerId).isEqualTo("ENC[CUST999]")
        assertThat(slot.captured.ifscCode).isEqualTo("ENC[IFSC1234]")
        assertThat(slot.captured.micrCode).isEqualTo("ENC[MICR5678]")
        assertThat(slot.captured.notes).isEqualTo("ENC[salary credit]")
    }

    @Test
    fun insertMultipleBankAccountData_shouldEncryptAllEntitiesInList() = runTest {
        val inputList = listOf(
            TestFixtures.createTestBankAccountDataEntity(key = 1, accountNumber = "111122223333"),
            TestFixtures.createTestBankAccountDataEntity(key = 2, accountNumber = "444455556666")
        )
        val slot = slot<List<BankAccountDataEntity>>()
        every { bankAccountDataDao.insertMultipleBankAccountData(capture(slot)) } returns Unit

        bankAccountDataDaoSecure.insertMultipleBankAccountData(inputList)

        assertThat(slot.captured).hasSize(2)
        assertThat(slot.captured[0].accountNumber).isEqualTo("ENC[111122223333]")
        assertThat(slot.captured[1].accountNumber).isEqualTo("ENC[444455556666]")
    }

    @Test
    fun getAllBankAccountData_shouldDecryptAndMaskAccountNumberInSearchFlow() = runTest {
        val encryptedSearchList = listOf(
            TestFixtures.createTestSearchBankAccountData(
                key = 1,
                title = "Savings",
                accountNumber = "ENC[987654321012]"
            )
        )
        every { bankAccountDataDao.getAllBankAccountData() } returns flowOf(encryptedSearchList)

        bankAccountDataDaoSecure.getAllBankAccountData().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            // 12 digits: first 8 masked, last 4 unmasked
            assertThat(items[0].accountNumber).isEqualTo("XXXXXXXX1012")
            awaitComplete()
        }
    }

    @Test
    fun getBankAccountDataByKey_shouldDecryptRetrievedEntity() = runTest {
        val encryptedEntity = TestFixtures.createTestBankAccountDataEntity(
            key = 7,
            title = "Swiss Bank",
            accountNumber = "ENC[CH930000000000001234]",
            customerName = "Secret Agent",
            customerId = "ENC[AGENT007]",
            ifscCode = "ENC[CHIFSC]",
            micrCode = "ENC[CHMICR]",
            notes = "ENC[classified account]"
        )
        coEvery { bankAccountDataDao.getBankAccountDataByKey(7) } returns encryptedEntity

        val decrypted = bankAccountDataDaoSecure.getBankAccountDataByKey(7)

        assertThat(decrypted.key).isEqualTo(7)
        assertThat(decrypted.accountNumber).isEqualTo("CH930000000000001234")
        assertThat(decrypted.customerName).isEqualTo("Secret Agent")
        assertThat(decrypted.customerId).isEqualTo("AGENT007")
        assertThat(decrypted.ifscCode).isEqualTo("CHIFSC")
        assertThat(decrypted.micrCode).isEqualTo("CHMICR")
        assertThat(decrypted.notes).isEqualTo("classified account")
    }

    @Test
    fun deleteBankAccountDataByKey_shouldDelegateToDao() = runTest {
        bankAccountDataDaoSecure.deleteBankAccountDataByKey(7)

        coVerify(exactly = 1) { bankAccountDataDao.deleteBankAccountDataByKey(7) }
    }

    @Test
    fun exportAllData_shouldDecryptExportBankAccountDataList() = runTest {
        val encryptedExport = listOf(
            TestFixtures.createTestExportBankAccountData(
                title = "Export Bank",
                accountNumber = "ENC[1234567890]",
                customerName = "Export Customer",
                customerId = "ENC[EXP123]",
                ifscCode = "ENC[EXIFSC]",
                micrCode = "ENC[EXMICR]",
                notes = "ENC[export notes]"
            )
        )
        coEvery { bankAccountDataDao.exportAllData() } returns encryptedExport

        val exported = bankAccountDataDaoSecure.exportAllData()

        assertThat(exported).hasSize(1)
        assertThat(exported[0].accountNumber).isEqualTo("1234567890")
        assertThat(exported[0].customerId).isEqualTo("EXP123")
        assertThat(exported[0].ifscCode).isEqualTo("EXIFSC")
        assertThat(exported[0].micrCode).isEqualTo("EXMICR")
        assertThat(exported[0].notes).isEqualTo("export notes")
    }

    @Test
    fun deleteAllData_shouldDelegateToDao() {
        bankAccountDataDaoSecure.deleteAllData()

        verify(exactly = 1) { bankAccountDataDao.deleteAllData() }
    }
}
