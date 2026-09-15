package com.andryoga.safebox.data.db.secureDao

import app.cash.turbine.test
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.AuthenticatorDataDao
import com.andryoga.safebox.data.db.entity.AuthenticatorDataEntity
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AuthenticatorDataDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var authenticatorDataDao: AuthenticatorDataDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var authenticatorDataDaoSecure: AuthenticatorDataDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        authenticatorDataDaoSecure = AuthenticatorDataDaoSecure(
            authenticatorDataDao = authenticatorDataDao,
            symmetricKeyUtils = symmetricKeyUtils,
        )
    }

    @Test
    fun upsertAuthenticatorData_shouldEncryptSecretKeyBeforePassingToDao() = runTest {
        val inputEntity = TestFixtures.createTestAuthenticatorDataEntity(
            title = "GitHub 2FA",
            secretKey = "JBSWY3DPEHPK3PXP",
        )
        val slot = slot<AuthenticatorDataEntity>()
        coEvery { authenticatorDataDao.upsertAuthenticatorData(capture(slot)) } returns Unit

        authenticatorDataDaoSecure.upsertAuthenticatorData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.title).isEqualTo("GitHub 2FA")
        assertThat(slot.captured.secretKey).isEqualTo("ENC[JBSWY3DPEHPK3PXP]")
    }

    @Test
    fun insertMultipleAuthenticatorData_shouldEncryptSecretKeyInAllEntities() = runTest {
        val inputList = listOf(
            TestFixtures.createTestAuthenticatorDataEntity(key = 1, secretKey = "SECRET1"),
            TestFixtures.createTestAuthenticatorDataEntity(key = 2, secretKey = "SECRET2"),
        )
        val slot = slot<List<AuthenticatorDataEntity>>()
        every { authenticatorDataDao.insertMultipleAuthenticatorData(capture(slot)) } returns Unit

        authenticatorDataDaoSecure.insertMultipleAuthenticatorData(inputList)

        assertThat(slot.captured).hasSize(2)
        assertThat(slot.captured[0].secretKey).isEqualTo("ENC[SECRET1]")
        assertThat(slot.captured[1].secretKey).isEqualTo("ENC[SECRET2]")
    }

    @Test
    fun getAllAuthenticatorData_shouldDecryptSecretKeyInSearchFlow() = runTest {
        val encryptedSearchList = listOf(
            TestFixtures.createTestSearchAuthenticatorData(
                key = 1,
                title = "GitHub",
                secretKey = "ENC[JBSWY3DPEHPK3PXP]",
            ),
        )
        every { authenticatorDataDao.getAllAuthenticatorData() } returns flowOf(encryptedSearchList)

        authenticatorDataDaoSecure.getAllAuthenticatorData().test {
            val items = awaitItem()
            assertThat(items).hasSize(1)
            assertThat(items[0].key).isEqualTo(1)
            assertThat(items[0].title).isEqualTo("GitHub")
            assertThat(items[0].secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
            awaitComplete()
        }
    }

    @Test
    fun getAuthenticatorDataByKey_shouldDecryptRetrievedEntitySecretKey() = runTest {
        val encryptedEntity = TestFixtures.createTestAuthenticatorDataEntity(
            key = 42,
            title = "Google Authenticator",
            secretKey = "ENC[HXDMVJECJJWSRB3H]",
        )
        coEvery { authenticatorDataDao.getAuthenticatorDataByKey(42) } returns encryptedEntity

        val decrypted = authenticatorDataDaoSecure.getAuthenticatorDataByKey(42)

        assertThat(decrypted.key).isEqualTo(42)
        assertThat(decrypted.title).isEqualTo("Google Authenticator")
        assertThat(decrypted.secretKey).isEqualTo("HXDMVJECJJWSRB3H")
    }

    @Test
    fun deleteAuthenticatorDataByKey_shouldDelegateToDao() = runTest {
        authenticatorDataDaoSecure.deleteAuthenticatorDataByKey(42)

        coVerify(exactly = 1) { authenticatorDataDao.deleteAuthenticatorDataByKey(42) }
    }

    @Test
    fun exportAllData_shouldDecryptSecretKeyInExportAuthenticatorDataList() = runTest {
        val encryptedExport = listOf(
            TestFixtures.createTestExportAuthenticatorData(
                title = "Exported 2FA",
                secretKey = "ENC[JBSWY3DPEHPK3PXP]",
            ),
        )
        coEvery { authenticatorDataDao.exportAllData() } returns encryptedExport

        val exported = authenticatorDataDaoSecure.exportAllData()

        assertThat(exported).hasSize(1)
        assertThat(exported[0].title).isEqualTo("Exported 2FA")
        assertThat(exported[0].secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
    }

    @Test
    fun deleteAllData_shouldDelegateToDao() {
        authenticatorDataDaoSecure.deleteAllData()

        verify(exactly = 1) { authenticatorDataDao.deleteAllData() }
    }
}
