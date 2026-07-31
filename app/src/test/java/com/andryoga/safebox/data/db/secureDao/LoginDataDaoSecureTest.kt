@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.LoginDataDao
import com.andryoga.safebox.data.db.entity.LoginDataEntity
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

class LoginDataDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var loginDataDao: LoginDataDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var loginDataDaoSecure: LoginDataDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        loginDataDaoSecure = LoginDataDaoSecure(
            loginDataDao = loginDataDao,
            symmetricKeyUtils = symmetricKeyUtils
        )
    }

    @Test
    fun upsertLoginData_shouldEncryptSensitiveFieldsBeforePassingToDao() = runTest {
        val inputEntity = TestFixtures.createTestLoginDataEntity(
            title = "GitHub",
            url = "https://github.com",
            userId = "octocat",
            password = "secretPassword",
            notes = "personal token"
        )
        val slot = slot<LoginDataEntity>()
        coEvery { loginDataDao.upsertLoginData(capture(slot)) } returns Unit

        loginDataDaoSecure.upsertLoginData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.title).isEqualTo("GitHub")
        assertThat(slot.captured.url).isEqualTo("ENC[https://github.com]")
        assertThat(slot.captured.userId).isEqualTo("ENC[octocat]")
        assertThat(slot.captured.password).isEqualTo("ENC[secretPassword]")
        assertThat(slot.captured.notes).isEqualTo("ENC[personal token]")
    }

    @Test
    fun upsertLoginData_withNullOptionalFields_shouldPreserveNullsAndEncryptUserId() = runTest {
        val inputEntity = TestFixtures.createTestLoginDataEntity(
            title = "Empty Optional Login",
            url = null,
            userId = "admin",
            password = null,
            notes = null
        )
        val slot = slot<LoginDataEntity>()
        coEvery { loginDataDao.upsertLoginData(capture(slot)) } returns Unit

        loginDataDaoSecure.upsertLoginData(inputEntity)

        assertThat(slot.captured.url).isNull()
        assertThat(slot.captured.password).isNull()
        assertThat(slot.captured.notes).isNull()
        assertThat(slot.captured.userId).isEqualTo("ENC[admin]")
    }

    @Test
    fun insertMultipleLoginData_shouldEncryptAllEntitiesInList() = runTest {
        val inputList = listOf(
            TestFixtures.createTestLoginDataEntity(key = 1, userId = "user1", password = "pass1"),
            TestFixtures.createTestLoginDataEntity(key = 2, userId = "user2", password = "pass2")
        )
        val slot = slot<List<LoginDataEntity>>()
        every { loginDataDao.insertMultipleLoginData(capture(slot)) } returns Unit

        loginDataDaoSecure.insertMultipleLoginData(inputList)

        assertThat(slot.captured).hasSize(2)
        assertThat(slot.captured[0].userId).isEqualTo("ENC[user1]")
        assertThat(slot.captured[1].userId).isEqualTo("ENC[user2]")
    }

    @Test
    fun getAllLoginData_shouldDecryptUserIdInSearchLoginDataFlow() = runTest {
        val encryptedSearchList = listOf(
            TestFixtures.createTestSearchLoginData(
                key = 1,
                title = "Google",
                userId = "ENC[user@gmail.com]"
            )
        )
        every { loginDataDao.getAllLoginData() } returns flowOf(encryptedSearchList)

        loginDataDaoSecure.getAllLoginData().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items[0].userId).isEqualTo("user@gmail.com")
            awaitComplete()
        }
    }

    @Test
    fun getLoginDataByKey_shouldDecryptRetrievedEntity() = runTest {
        val encryptedEntity = TestFixtures.createTestLoginDataEntity(
            key = 10,
            title = "AWS",
            url = "ENC[https://aws.amazon.com]",
            userId = "ENC[root_user]",
            password = "ENC[awsMasterKey]",
            notes = "ENC[mfa required]"
        )
        coEvery { loginDataDao.getLoginDataByKey(10) } returns encryptedEntity

        val decrypted = loginDataDaoSecure.getLoginDataByKey(10)

        assertThat(decrypted.key).isEqualTo(10)
        assertThat(decrypted.title).isEqualTo("AWS")
        assertThat(decrypted.url).isEqualTo("https://aws.amazon.com")
        assertThat(decrypted.userId).isEqualTo("root_user")
        assertThat(decrypted.password).isEqualTo("awsMasterKey")
        assertThat(decrypted.notes).isEqualTo("mfa required")
    }

    @Test
    fun deleteLoginDataByKey_shouldDelegateToDao() = runTest {
        loginDataDaoSecure.deleteLoginDataByKey(10)

        coVerify(exactly = 1) { loginDataDao.deleteLoginDataByKey(10) }
    }

    @Test
    fun exportAllData_shouldDecryptExportLoginDataList() = runTest {
        val encryptedExport = listOf(
            TestFixtures.createTestExportLoginData(
                title = "PayPal",
                url = "ENC[https://paypal.com]",
                userId = "ENC[buyer@paypal.com]",
                password = "ENC[payPass]",
                notes = "ENC[2fa active]"
            )
        )
        coEvery { loginDataDao.exportAllData() } returns encryptedExport

        val exported = loginDataDaoSecure.exportAllData()

        assertThat(exported).hasSize(1)
        assertThat(exported[0].userId).isEqualTo("buyer@paypal.com")
        assertThat(exported[0].password).isEqualTo("payPass")
        assertThat(exported[0].url).isEqualTo("https://paypal.com")
        assertThat(exported[0].notes).isEqualTo("2fa active")
    }

    @Test
    fun deleteAllData_shouldDelegateToDao() {
        loginDataDaoSecure.deleteAllData()

        verify(exactly = 1) { loginDataDao.deleteAllData() }
    }
}
