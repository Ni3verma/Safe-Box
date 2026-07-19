@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.secureDao.BankAccountDataDaoSecure
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

class BankAccountDataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var bankAccountDataDaoSecure: BankAccountDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: BankAccountDataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        repository = BankAccountDataRepositoryImpl(
            bankAccountDataDaoSecure = bankAccountDataDaoSecure,
            analyticsHelper = analyticsHelper
        )
    }

    @Test
    fun upsertBankAccountData_whenIdIsNull_shouldLogNewBankAccountAnalyticsAndCallDaoSecure() =
        runTest {
            val accountData =
                TestFixtures.createTestBankAccountData(id = null, title = "Savings Account")

            repository.upsertBankAccountData(accountData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_ACCOUNT)).isTrue()
            coVerify(exactly = 1) {
                bankAccountDataDaoSecure.upsertBankAccountData(match { it.key == 0 && it.title == "Savings Account" })
            }
        }

    @Test
    fun upsertBankAccountData_whenIdIsZero_shouldLogNewBankAccountAnalyticsAndCallDaoSecure() =
        runTest {
            val accountData =
                TestFixtures.createTestBankAccountData(id = 0, title = "Checking Account")

            repository.upsertBankAccountData(accountData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_ACCOUNT)).isTrue()
            coVerify(exactly = 1) {
                bankAccountDataDaoSecure.upsertBankAccountData(match { it.key == 0 && it.title == "Checking Account" })
            }
        }

    @Test
    fun upsertBankAccountData_whenIdIsPositive_shouldNotLogAnalyticsAndCallDaoSecure() = runTest {
        val accountData = TestFixtures.createTestBankAccountData(id = 8, title = "Current Account")

        repository.upsertBankAccountData(accountData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_BANK_ACCOUNT)).isFalse()
        coVerify(exactly = 1) {
            bankAccountDataDaoSecure.upsertBankAccountData(match { it.key == 8 && it.title == "Current Account" })
        }
    }

    @Test
    fun getAllBankAccountData_shouldReturnFlowFromDaoSecure() = runTest {
        val searchList =
            listOf(TestFixtures.createTestSearchBankAccountData(key = 3, title = "Deposit Account"))
        every { bankAccountDataDaoSecure.getAllBankAccountData() } returns flowOf(searchList)

        repository.getAllBankAccountData().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getBankAccountDataByKey_shouldReturnMappedBankAccountDataDomainModel() = runTest {
        val dbEntity = TestFixtures.createTestBankAccountDataEntity(
            key = 20,
            title = "Salary Account",
            accountNumber = "1122334455"
        )
        coEvery { bankAccountDataDaoSecure.getBankAccountDataByKey(20) } returns dbEntity

        val result = repository.getBankAccountDataByKey(20)

        assertThat(result.id).isEqualTo(20)
        assertThat(result.title).isEqualTo("Salary Account")
        assertThat(result.accountNo).isEqualTo("1122334455")
    }

    @Test
    fun deleteBankAccountDataByKey_shouldDelegateToDaoSecure() = runTest {
        repository.deleteBankAccountDataByKey(20)

        coVerify(exactly = 1) { bankAccountDataDaoSecure.deleteBankAccountDataByKey(20) }
    }
}
