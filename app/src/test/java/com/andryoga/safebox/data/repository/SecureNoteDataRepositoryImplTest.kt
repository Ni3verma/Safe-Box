@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.repository

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.db.secureDao.SecureNoteDataDaoSecure
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

class SecureNoteDataRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var secureNoteDataDaoSecure: SecureNoteDataDaoSecure

    private lateinit var analyticsHelper: FakeAnalyticsHelper
    private lateinit var repository: SecureNoteDataRepositoryImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        analyticsHelper = FakeAnalyticsHelper()
        repository = SecureNoteDataRepositoryImpl(
            secureNoteDataDaoSecure = secureNoteDataDaoSecure,
            analyticsHelper = analyticsHelper
        )
    }

    @Test
    fun upsertSecureNoteData_whenIdIsNull_shouldLogNewSecureNoteAnalyticsAndCallDaoSecure() =
        runTest {
            val noteData = TestFixtures.createTestNoteData(id = null, title = "Recovery Keys")

            repository.upsertSecureNoteData(noteData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_SECURE_NOTE)).isTrue()
            coVerify(exactly = 1) {
                secureNoteDataDaoSecure.upsertSecretNoteData(match { it.key == 0 && it.title == "Recovery Keys" })
            }
        }

    @Test
    fun upsertSecureNoteData_whenIdIsZero_shouldLogNewSecureNoteAnalyticsAndCallDaoSecure() =
        runTest {
            val noteData = TestFixtures.createTestNoteData(id = 0, title = "WIFI Password")

            repository.upsertSecureNoteData(noteData)

            assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_SECURE_NOTE)).isTrue()
            coVerify(exactly = 1) {
                secureNoteDataDaoSecure.upsertSecretNoteData(match { it.key == 0 && it.title == "WIFI Password" })
            }
        }

    @Test
    fun upsertSecureNoteData_whenIdIsPositive_shouldNotLogAnalyticsAndCallDaoSecure() = runTest {
        val noteData = TestFixtures.createTestNoteData(id = 11, title = "Server Credentials")

        repository.upsertSecureNoteData(noteData)

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.NEW_SECURE_NOTE)).isFalse()
        coVerify(exactly = 1) {
            secureNoteDataDaoSecure.upsertSecretNoteData(match { it.key == 11 && it.title == "Server Credentials" })
        }
    }

    @Test
    fun getAllSecureNoteData_shouldReturnFlowFromDaoSecure() = runTest {
        val searchList =
            listOf(TestFixtures.createTestSearchSecureNoteData(key = 4, title = "Door Code"))
        every { secureNoteDataDaoSecure.getAllSecretNoteData() } returns flowOf(searchList)

        repository.getAllSecureNoteData().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(searchList)
            awaitComplete()
        }
    }

    @Test
    fun getSecureNoteDataByKey_shouldReturnMappedNoteDataDomainModel() = runTest {
        val dbEntity = TestFixtures.createTestSecureNoteDataEntity(
            key = 25,
            title = "Safe Combination",
            notes = "44-22-11"
        )
        coEvery { secureNoteDataDaoSecure.getSecretNoteDataByKey(25) } returns dbEntity

        val result = repository.getSecureNoteDataByKey(25)

        assertThat(result.id).isEqualTo(25)
        assertThat(result.title).isEqualTo("Safe Combination")
        assertThat(result.notes).isEqualTo("44-22-11")
    }

    @Test
    fun deleteSecureNoteDataByKey_shouldDelegateToDaoSecure() = runTest {
        repository.deleteSecureNoteDataByKey(25)

        coVerify(exactly = 1) { secureNoteDataDaoSecure.deleteSecretNoteDataByKey(25) }
    }
}
