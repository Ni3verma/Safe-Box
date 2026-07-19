@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.secureDao.BankCardDataDaoSecure
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BankCardDataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var bankCardDataDaoSecure: BankCardDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: BankCardDataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        repository = BankCardDataRepositoryImpl(
            bankCardDataDaoSecure = bankCardDataDaoSecure,
            analyticsHelper = analyticsHelper
        )
    }

    @Test
    fun upsertBankCardData_whenIdIsNull_shouldLogNewBankCardAnalyticsAndCallDaoSecure() = runTest {
        val cardData = TestFixtures.createTestCardData(id = null, title = "Visa Platinum")

        repository.upsertBankCardData(cardData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_CARD)).isTrue()
        coVerify(exactly = 1) {
            bankCardDataDaoSecure.upsertBankCardData(match { it.key == 0 && it.title == "Visa Platinum" })
        }
    }

    @Test
    fun upsertBankCardData_whenIdIsZero_shouldLogNewBankCardAnalyticsAndCallDaoSecure() = runTest {
        val cardData = TestFixtures.createTestCardData(id = 0, title = "Mastercard")

        repository.upsertBankCardData(cardData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_CARD)).isTrue()
        coVerify(exactly = 1) {
            bankCardDataDaoSecure.upsertBankCardData(match { it.key == 0 && it.title == "Mastercard" })
        }
    }

    @Test
    fun upsertBankCardData_whenIdIsPositive_shouldNotLogAnalyticsAndCallDaoSecure() = runTest {
        val cardData = TestFixtures.createTestCardData(id = 7, title = "Amex Gold")

        repository.upsertBankCardData(cardData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_CARD)).isFalse()
        coVerify(exactly = 1) {
            bankCardDataDaoSecure.upsertBankCardData(match { it.key == 7 && it.title == "Amex Gold" })
        }
    }

    @Test
    fun getAllBankCardData_shouldReturnFlowFromDaoSecure() = runTest {
        val searchList =
            listOf(TestFixtures.createTestSearchBankCardData(key = 2, title = "Debit Card"))
        every { bankCardDataDaoSecure.getAllBankCardData() } returns flowOf(searchList)

        repository.getAllBankCardData().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getBankCardDataByKey_shouldReturnMappedCardDataDomainModel() = runTest {
        val dbEntity = TestFixtures.createTestBankCardDataEntity(
            key = 15,
            title = "Forex Card",
            number = "4111222233334444"
        )
        coEvery { bankCardDataDaoSecure.getBankCardDataByKey(15) } returns dbEntity

        val result = repository.getBankCardDataByKey(15)

        assertThat(result.id).isEqualTo(15)
        assertThat(result.title).isEqualTo("Forex Card")
        assertThat(result.number).isEqualTo("4111222233334444")
    }

    @Test
    fun deleteBankCardDataByKey_shouldDelegateToDaoSecure() = runTest {
        repository.deleteBankCardDataByKey(15)

        coVerify(exactly = 1) { bankCardDataDaoSecure.deleteBankCardDataByKey(15) }
    }
}
