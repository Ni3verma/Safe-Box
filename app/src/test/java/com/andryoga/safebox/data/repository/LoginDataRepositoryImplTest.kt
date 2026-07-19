@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.secureDao.LoginDataDaoSecure
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

class LoginDataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var loginDataDaoSecure: LoginDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: LoginDataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        repository = LoginDataRepositoryImpl(
            loginDataDaoSecure = loginDataDaoSecure,
            analyticsHelper = analyticsHelper
        )
    }

    @Test
    fun upsertLoginData_whenIdIsNull_shouldLogNewLoginAnalyticsAndCallDaoSecure() = runTest {
        val domainData = TestFixtures.createTestLoginData(id = null, title = "GitHub")

        repository.upsertLoginData(domainData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_LOGIN)).isTrue()
        coVerify(exactly = 1) {
            loginDataDaoSecure.upsertLoginData(match { it.key == 0 && it.title == "GitHub" })
        }
    }

    @Test
    fun upsertLoginData_whenIdIsZero_shouldLogNewLoginAnalyticsAndCallDaoSecure() = runTest {
        val domainData = TestFixtures.createTestLoginData(id = 0, title = "GitLab")

        repository.upsertLoginData(domainData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_LOGIN)).isTrue()
        coVerify(exactly = 1) {
            loginDataDaoSecure.upsertLoginData(match { it.key == 0 && it.title == "GitLab" })
        }
    }

    @Test
    fun upsertLoginData_whenIdIsPositive_shouldNotLogAnalyticsAndCallDaoSecure() = runTest {
        val domainData = TestFixtures.createTestLoginData(id = 42, title = "Existing Login")

        repository.upsertLoginData(domainData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_LOGIN)).isFalse()
        coVerify(exactly = 1) {
            loginDataDaoSecure.upsertLoginData(match { it.key == 42 && it.title == "Existing Login" })
        }
    }

    @Test
    fun getAllLoginData_shouldReturnFlowFromDaoSecure() = runTest {
        val searchList = listOf(TestFixtures.createTestSearchLoginData(key = 1, title = "Google"))
        every { loginDataDaoSecure.getAllLoginData() } returns flowOf(searchList)

        repository.getAllLoginData().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getLoginDataByKey_shouldReturnMappedDomainModel() = runTest {
        val dbEntity = TestFixtures.createTestLoginDataEntity(
            key = 9,
            title = "Amazon",
            userId = "buyer@amazon.com"
        )
        coEvery { loginDataDaoSecure.getLoginDataByKey(9) } returns dbEntity

        val result = repository.getLoginDataByKey(9)

        assertThat(result.id).isEqualTo(9)
        assertThat(result.title).isEqualTo("Amazon")
        assertThat(result.userId).isEqualTo("buyer@amazon.com")
    }

    @Test
    fun deleteLoginDataByKey_shouldDelegateToDaoSecure() = runTest {
        repository.deleteLoginDataByKey(99)

        coVerify(exactly = 1) { loginDataDaoSecure.deleteLoginDataByKey(99) }
    }
}
