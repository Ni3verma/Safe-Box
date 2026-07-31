@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.BankCardDataDao
import com.andryoga.safebox.data.db.entity.BankCardDataEntity
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

class BankCardDataDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var bankCardDataDao: BankCardDataDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var bankCardDataDaoSecure: BankCardDataDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        bankCardDataDaoSecure = BankCardDataDaoSecure(
            bankCardDataDao = bankCardDataDao,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    @Test
    fun upsertBankCardData_shouldEncryptSensitiveFieldsBeforePassingToDao() = runTest {
        val inputEntity = TestFixtures.createTestBankCardDataEntity(
            title = "Visa Credit",
            name = "John Doe",
            number = "1234567812345678",
            pin = "4321",
            cvv = "123",
            expiryDate = "1228",
            notes = "primary card"
        )
        val slot = slot<BankCardDataEntity>()
        coEvery { bankCardDataDao.upsertBankCardData(capture(slot)) } returns Unit

        bankCardDataDaoSecure.upsertBankCardData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.title).isEqualTo("Visa Credit")
        assertThat(slot.captured.name).isEqualTo("ENC[John Doe]")
        assertThat(slot.captured.number).isEqualTo("ENC[1234567812345678]")
        assertThat(slot.captured.pin).isEqualTo("ENC[4321]")
        assertThat(slot.captured.cvv).isEqualTo("ENC[123]")
        assertThat(slot.captured.expiryDate).isEqualTo("ENC[1228]")
        assertThat(slot.captured.notes).isEqualTo("ENC[primary card]")
    }

    @Test
    fun insertMultipleBankCardData_shouldEncryptAllEntitiesInList() = runTest {
        val inputList = listOf(
            TestFixtures.createTestBankCardDataEntity(key = 1, number = "1111222233334444"),
            TestFixtures.createTestBankCardDataEntity(key = 2, number = "5555666677778888")
        )
        val slot = slot<List<BankCardDataEntity>>()
        every { bankCardDataDao.insertMultipleBankCardData(capture(slot)) } returns Unit

        bankCardDataDaoSecure.insertMultipleBankCardData(inputList)

        assertThat(slot.captured).hasSize(2)
        assertThat(slot.captured[0].number).isEqualTo("ENC[1111222233334444]")
        assertThat(slot.captured[1].number).isEqualTo("ENC[5555666677778888]")
    }

    @Test
    fun getAllBankCardData_shouldDecryptAndMaskCardNumberInSearchFlow() = runTest {
        val encryptedSearchList = listOf(
            TestFixtures.createTestSearchBankCardData(
                key = 1,
                title = "Debit",
                number = "ENC[1234567812345678]"
            )
        )
        every { bankCardDataDao.getAllBankCardData() } returns flowOf(encryptedSearchList)

        bankCardDataDaoSecure.getAllBankCardData().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            // 16 digits: first 12 replaced with 'X', last 4 left unmasked
            assertThat(items[0].number).isEqualTo("XXXXXXXXXXXX5678")
            awaitComplete()
        }
    }

    @Test
    fun getBankCardDataByKey_shouldDecryptEntityAndStripSlashesFromLegacyExpiryDate() = runTest {
        val encryptedEntity = TestFixtures.createTestBankCardDataEntity(
            key = 5,
            title = "Old Legacy Card",
            name = "ENC[Alice]",
            number = "ENC[9876543210987654]",
            pin = "ENC[9999]",
            cvv = "ENC[777]",
            expiryDate = "ENC[12/28]", // Contains legacy slash
            notes = "ENC[old card]"
        )
        coEvery { bankCardDataDao.getBankCardDataByKey(5) } returns encryptedEntity

        val decrypted = bankCardDataDaoSecure.getBankCardDataByKey(5)

        assertThat(decrypted.key).isEqualTo(5)
        assertThat(decrypted.name).isEqualTo("Alice")
        assertThat(decrypted.number).isEqualTo("9876543210987654")
        assertThat(decrypted.pin).isEqualTo("9999")
        assertThat(decrypted.cvv).isEqualTo("777")
        assertThat(decrypted.expiryDate).isEqualTo("1228") // Slashes stripped
        assertThat(decrypted.notes).isEqualTo("old card")
    }

    @Test
    fun deleteBankCardDataByKey_shouldDelegateToDao() = runTest {
        bankCardDataDaoSecure.deleteBankCardDataByKey(5)

        coVerify(exactly = 1) { bankCardDataDao.deleteBankCardDataByKey(5) }
    }

    @Test
    fun exportAllData_shouldDecryptExportBankCardDataList() = runTest {
        val encryptedExport = listOf(
            TestFixtures.createTestExportBankCardData(
                title = "Export Card",
                name = "ENC[Bob]",
                number = "ENC[4111222233334444]",
                pin = "ENC[1111]",
                cvv = "ENC[000]",
                expiryDate = "ENC[0530]",
                notes = "ENC[backup card]"
            )
        )
        coEvery { bankCardDataDao.exportAllData() } returns encryptedExport

        val exported = bankCardDataDaoSecure.exportAllData()

        assertThat(exported).hasSize(1)
        assertThat(exported[0].name).isEqualTo("Bob")
        assertThat(exported[0].number).isEqualTo("4111222233334444")
        assertThat(exported[0].pin).isEqualTo("1111")
        assertThat(exported[0].cvv).isEqualTo("000")
        assertThat(exported[0].expiryDate).isEqualTo("0530")
        assertThat(exported[0].notes).isEqualTo("backup card")
    }

    @Test
    fun deleteAllData_shouldDelegateToDao() {
        bankCardDataDaoSecure.deleteAllData()

        verify(exactly = 1) { bankCardDataDao.deleteAllData() }
    }
}
