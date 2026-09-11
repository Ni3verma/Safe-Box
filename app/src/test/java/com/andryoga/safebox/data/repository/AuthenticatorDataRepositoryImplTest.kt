package com.andryoga.safebox.data.repository

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.secureDao.AuthenticatorDataDaoSecure
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthenticatorDataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var authenticatorDataDaoSecure: AuthenticatorDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: AuthenticatorDataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        repository = AuthenticatorDataRepositoryImpl(
            authenticatorDataDaoSecure = authenticatorDataDaoSecure,
            analyticsHelper = analyticsHelper,
        )
    }

    @Test
    fun upsertAuthenticatorData_whenIdIsNull_shouldLogNewAuthenticatorAnalyticsAndCallDaoSecure() =
        runTest {
            val authenticatorData = TestFixtures.createTestAuthenticatorData(
                id = null,
                title = "GitHub 2FA",
            )

            repository.upsertAuthenticatorData(authenticatorData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_AUTHENTICATOR)).isTrue()
            coVerify(exactly = 1) {
                authenticatorDataDaoSecure.upsertAuthenticatorData(match { it.key == 0 && it.title == "GitHub 2FA" })
            }
        }

    @Test
    fun upsertAuthenticatorData_whenIdIsZero_shouldLogNewAuthenticatorAnalyticsAndCallDaoSecure() =
        runTest {
            val authenticatorData = TestFixtures.createTestAuthenticatorData(
                id = 0,
                title = "AWS 2FA",
            )

            repository.upsertAuthenticatorData(authenticatorData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_AUTHENTICATOR)).isTrue()
            coVerify(exactly = 1) {
                authenticatorDataDaoSecure.upsertAuthenticatorData(match { it.key == 0 && it.title == "AWS 2FA" })
            }
        }

    @Test
    fun upsertAuthenticatorData_whenIdIsPositive_shouldNotLogAnalyticsAndCallDaoSecure() =
        runTest {
            val authenticatorData = TestFixtures.createTestAuthenticatorData(
                id = 15,
                title = "Google 2FA",
            )

            repository.upsertAuthenticatorData(authenticatorData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_AUTHENTICATOR)).isFalse()
            coVerify(exactly = 1) {
                authenticatorDataDaoSecure.upsertAuthenticatorData(match { it.key == 15 && it.title == "Google 2FA" })
            }
        }

    @Test
    fun getAllAuthenticatorData_shouldReturnFlowFromDaoSecure() = runTest {
        val searchList = listOf(
            TestFixtures.createTestSearchAuthenticatorData(key = 7, title = "Cloudflare 2FA"),
        )
        every { authenticatorDataDaoSecure.getAllAuthenticatorData() } returns flowOf(searchList)

        repository.getAllAuthenticatorData().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getAuthenticatorDataByKey_shouldReturnMappedAuthenticatorDataDomainModel() = runTest {
        val dbEntity = TestFixtures.createTestAuthenticatorDataEntity(
            key = 30,
            title = "Discord 2FA",
            secretKey = "JBSWY3DPEHPK3PXP",
        )
        coEvery { authenticatorDataDaoSecure.getAuthenticatorDataByKey(30) } returns dbEntity

        val result = repository.getAuthenticatorDataByKey(30)

        assertThat(result.id).isEqualTo(30)
        assertThat(result.title).isEqualTo("Discord 2FA")
        assertThat(result.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun deleteAuthenticatorDataByKey_shouldDelegateToDaoSecure() = runTest {
        repository.deleteAuthenticatorDataByKey(30)

        coVerify(exactly = 1) { authenticatorDataDaoSecure.deleteAuthenticatorDataByKey(30) }
    }
}
