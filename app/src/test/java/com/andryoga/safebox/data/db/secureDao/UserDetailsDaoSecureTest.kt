@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.data.db.secureDao

import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.data.db.dao.UserDetailsDao
import com.andryoga.safebox.data.db.dao.VaultMetaDao
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.security.AesGcmFieldCipher
import com.andryoga.safebox.security.Base64Rule
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.GeneralSecurityException

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
}
