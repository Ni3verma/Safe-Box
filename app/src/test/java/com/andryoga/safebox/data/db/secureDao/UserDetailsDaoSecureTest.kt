@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.test.fakes.FakeSymmetricKeyUtils
import com.andryoga.safebox.test.fixtures.TestFixtures
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class UserDetailsDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDao: UserDetailsDao

    @MockK
    lateinit var vaultMetaDao: VaultMetaDao

    private lateinit var symmetricKeyUtils: FakeSymmetricKeyUtils
    private lateinit var userDetailsDaoSecure: UserDetailsDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        symmetricKeyUtils = FakeSymmetricKeyUtils()
        userDetailsDaoSecure = UserDetailsDaoSecure(
            userDetailsDao = userDetailsDao,
            vaultMetaDao = vaultMetaDao,
            symmetricKeyUtils = symmetricKeyUtils,
        )
    }

    private fun givenCryptoVersion(version: String?) {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns version
    }

    @Test
    fun insertUserDetailsData_whenVaultIsLegacy_shouldEncryptHintBeforeDaoInsert() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        val inputEntity = TestFixtures.createTestUserDetailsEntity(hint = "myHint")
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.hint).isEqualTo("ENC[myHint]")
        assertThat(slot.captured.uid).isEqualTo(inputEntity.uid)
        assertThat(slot.captured.key).isEqualTo(inputEntity.key)
    }

    @Test
    fun insertUserDetailsData_whenCryptoVersionRowIsAbsent_shouldEncryptHintAsLegacy() = runTest {
        givenCryptoVersion(null)
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(TestFixtures.createTestUserDetailsEntity(hint = "myHint"))

        assertThat(slot.captured.hint).isEqualTo("ENC[myHint]")
    }

    @Test
    fun insertUserDetailsData_whenVaultIsEnvelope_shouldStoreHintAsPlaintext() = runTest {
        givenCryptoVersion(CryptoVersion.ENVELOPE)
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(TestFixtures.createTestUserDetailsEntity(hint = "myHint"))

        assertThat(slot.captured.hint).isEqualTo("myHint")
    }

    @Test
    fun insertUserDetailsData_whenHintIsNull_shouldKeepNullHintWithoutReadingCryptoVersion() = runTest {
        val inputEntity = TestFixtures.createTestUserDetailsEntity(hint = null)
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(inputEntity)

        assertThat(slot.captured.hint).isNull()
        coVerify(exactly = 0) { vaultMetaDao.get(any()) }
    }

    @Test
    fun getHint_whenVaultIsLegacy_shouldDecryptHint() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        coEvery { userDetailsDao.getHint() } returns "ENC[secretHint]"

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("secretHint")
    }

    @Test
    fun getHint_whenCryptoVersionRowIsAbsent_shouldDecryptHintAsLegacy() = runTest {
        givenCryptoVersion(null)
        coEvery { userDetailsDao.getHint() } returns "ENC[secretHint]"

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("secretHint")
    }

    @Test
    fun getHint_whenVaultIsEnvelope_shouldReturnStoredHintWithoutDecrypting() = runTest {
        givenCryptoVersion(CryptoVersion.ENVELOPE)
        coEvery { userDetailsDao.getHint() } returns "plain hint"

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("plain hint")
    }

    @Test
    fun getHint_whenHintIsNull_shouldReturnNullWithoutReadingCryptoVersion() = runTest {
        coEvery { userDetailsDao.getHint() } returns null

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isNull()
        coVerify(exactly = 0) { vaultMetaDao.get(any()) }
    }

    @Test
    fun getUid_shouldDelegateToUserDetailsDao() = runTest {
        coEvery { userDetailsDao.getUid() } returns "unique-user-id"

        val uid = userDetailsDaoSecure.getUid()

        assertThat(uid).isEqualTo("unique-user-id")
    }

    @Test
    fun updateHint_whenVaultIsLegacy_shouldEncryptHintBeforeDaoUpdate() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        val updateDate = Date(1700000000000L)

        userDetailsDaoSecure.updateHint("new hint", updateDate)

        coVerify { userDetailsDao.updateHint("ENC[new hint]", updateDate) }
    }

    @Test
    fun updateHint_whenVaultIsEnvelope_shouldStoreHintAsPlaintext() = runTest {
        givenCryptoVersion(CryptoVersion.ENVELOPE)
        val updateDate = Date(1700000000000L)

        userDetailsDaoSecure.updateHint("new hint", updateDate)

        coVerify { userDetailsDao.updateHint("new hint", updateDate) }
    }
}
