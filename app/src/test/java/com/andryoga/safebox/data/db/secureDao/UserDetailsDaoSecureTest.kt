@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.entity.UserDetailsEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.Base64Rule
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
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
import java.security.GeneralSecurityException
import java.util.Date

/**
 * The hint path: a legacy vault's hint goes through the legacy Keystore handle from
 * the gateway (the holder is locked on the login screen), an envelope vault's hint is plaintext.
 */
class UserDetailsDaoSecureTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val base64Rule = Base64Rule()

    @MockK(relaxUnitFun = true)
    lateinit var userDetailsDao: UserDetailsDao

    @MockK
    lateinit var vaultMetaDao: VaultMetaDao

    private lateinit var keystoreGateway: FakeKeystoreGateway
    private lateinit var userDetailsDaoSecure: UserDetailsDaoSecure

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        keystoreGateway = FakeKeystoreGateway().apply { legacyKey = CryptoTestFixtures.TEST_AES_KEY_256 }
        userDetailsDaoSecure = UserDetailsDaoSecure(
            userDetailsDao = userDetailsDao,
            vaultMetaDao = vaultMetaDao,
            keystoreGateway = keystoreGateway,
        )
    }

    private fun givenCryptoVersion(version: String?) {
        coEvery { vaultMetaDao.get(VaultMetaKey.CRYPTO_VERSION) } returns version
    }

    private fun legacyCiphertextOf(plaintext: String): String =
        AesGcmFieldCipher.encrypt(plaintext, CryptoTestFixtures.TEST_AES_KEY_256)

    private fun decryptWithLegacyKey(stored: String): String =
        AesGcmFieldCipher.decrypt(stored, CryptoTestFixtures.TEST_AES_KEY_256)

    @Test
    fun insertUserDetailsData_whenVaultIsLegacy_shouldEncryptHintUnderTheLegacyKey() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        val inputEntity = TestFixtures.createTestUserDetailsEntity(hint = "myHint")
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(inputEntity)

        assertThat(slot.isCaptured).isTrue()
        assertThat(slot.captured.hint).isNotEqualTo("myHint")
        assertThat(decryptWithLegacyKey(slot.captured.hint!!)).isEqualTo("myHint")
        assertThat(slot.captured.uid).isEqualTo(inputEntity.uid)
        assertThat(slot.captured.key).isEqualTo(inputEntity.key)
    }

    @Test
    fun insertUserDetailsData_whenCryptoVersionRowIsAbsent_shouldEncryptHintAsLegacy() = runTest {
        givenCryptoVersion(null)
        val slot = slot<UserDetailsEntity>()
        coEvery { userDetailsDao.insertUserDetailsData(capture(slot)) } returns Unit

        userDetailsDaoSecure.insertUserDetailsData(TestFixtures.createTestUserDetailsEntity(hint = "myHint"))

        assertThat(decryptWithLegacyKey(slot.captured.hint!!)).isEqualTo("myHint")
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
    fun insertUserDetailsData_whenVaultIsLegacyAndKeyIsMissing_shouldFailInsteadOfWritingPlaintext() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        keystoreGateway.legacyKey = null

        val thrown = runCatching {
            userDetailsDaoSecure.insertUserDetailsData(TestFixtures.createTestUserDetailsEntity(hint = "myHint"))
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        coVerify(exactly = 0) { userDetailsDao.insertUserDetailsData(any()) }
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
    fun getHint_whenVaultIsLegacy_shouldDecryptHintWithTheLegacyKey() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        coEvery { userDetailsDao.getHint() } returns legacyCiphertextOf("secretHint")

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("secretHint")
    }

    @Test
    fun getHint_whenCryptoVersionRowIsAbsent_shouldDecryptHintAsLegacy() = runTest {
        givenCryptoVersion(null)
        coEvery { userDetailsDao.getHint() } returns legacyCiphertextOf("secretHint")

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isEqualTo("secretHint")
    }

    @Test
    fun getHint_whenVaultIsLegacyAndKeyIsMissing_shouldReturnNullInsteadOfThrowing() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        coEvery { userDetailsDao.getHint() } returns legacyCiphertextOf("secretHint")
        keystoreGateway.legacyKey = null

        val hint = userDetailsDaoSecure.getHint()

        assertThat(hint).isNull()
    }

    @Test
    fun getHint_whenVaultIsLegacyAndADifferentKeyIsInstalled_shouldFailOnTheGcmTag() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        coEvery { userDetailsDao.getHint() } returns legacyCiphertextOf("secretHint")
        keystoreGateway.legacyKey = CryptoTestFixtures.TEST_SECONDARY_AES_KEY

        val thrown = runCatching { userDetailsDaoSecure.getHint() }.exceptionOrNull()

        // A regenerated key would read every hint like this; the gateway never regenerates.
        assertThat(thrown).isInstanceOf(GeneralSecurityException::class.java)
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
    fun updateHint_whenVaultIsLegacy_shouldEncryptHintUnderTheLegacyKey() = runTest {
        givenCryptoVersion(CryptoVersion.LEGACY)
        val updateDate = Date(1700000000000L)
        val slot = slot<String>()
        coEvery { userDetailsDao.updateHint(capture(slot), updateDate) } returns Unit

        userDetailsDaoSecure.updateHint("new hint", updateDate)

        assertThat(slot.captured).isNotEqualTo("new hint")
        assertThat(decryptWithLegacyKey(slot.captured)).isEqualTo("new hint")
    }

    @Test
    fun updateHint_whenVaultIsEnvelope_shouldStoreHintAsPlaintext() = runTest {
        givenCryptoVersion(CryptoVersion.ENVELOPE)
        val updateDate = Date(1700000000000L)

        userDetailsDaoSecure.updateHint("new hint", updateDate)

        coVerify { userDetailsDao.updateHint("new hint", updateDate) }
    }
}
