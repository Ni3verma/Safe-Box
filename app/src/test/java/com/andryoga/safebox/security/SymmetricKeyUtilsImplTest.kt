package com.andryoga.safebox.security

import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The field cipher takes its key from the session holder on every call, so a
 * locked vault fails closed and a DEK session and a legacy session both round-trip.
 */
class SymmetricKeyUtilsImplTest {

    @get:Rule
    val base64Rule = Base64Rule()

    private lateinit var sessionKeyHolder: SessionKeyHolderImpl
    private lateinit var symmetricKeyUtils: SymmetricKeyUtilsImpl

    @Before
    fun setUp() {
        sessionKeyHolder = SessionKeyHolderImpl()
        symmetricKeyUtils = SymmetricKeyUtilsImpl(sessionKeyHolder)
    }

    @Test
    fun encrypt_whenVaultIsLocked_throwsSessionLockedException() {
        assertThrows(SessionLockedException::class.java) {
            symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)
        }
    }

    @Test
    fun decrypt_whenVaultIsLocked_throwsSessionLockedException() {
        sessionKeyHolder.setSessionKey(DEK_BYTES)
        val encrypted = symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)
        sessionKeyHolder.clearSessionKey()

        assertThrows(SessionLockedException::class.java) {
            symmetricKeyUtils.decrypt(encrypted)
        }
    }

    @Test
    fun encryptAndDecrypt_inDekSession_returnsOriginalPlaintext() {
        sessionKeyHolder.setSessionKey(DEK_BYTES)

        val encrypted = symmetricKeyUtils.encrypt(CryptoTestFixtures.UNICODE_PLAINTEXT)
        val decrypted = symmetricKeyUtils.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(CryptoTestFixtures.UNICODE_PLAINTEXT)
    }

    @Test
    fun encrypt_inDekSession_isReadableWithTheRawDekOutsideTheHolder() {
        sessionKeyHolder.setSessionKey(DEK_BYTES)

        val encrypted = symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)

        val rawDek: SecretKey = SecretKeySpec(DEK_BYTES, "AES")
        assertThat(AesGcmFieldCipher.decrypt(encrypted, rawDek))
            .isEqualTo(CryptoTestFixtures.SAMPLE_PLAINTEXT)
    }

    @Test
    fun encryptAndDecrypt_inLegacySession_useTheLegacyHandle() {
        val legacyHandle = CryptoTestFixtures.TEST_AES_KEY_256
        sessionKeyHolder.setLegacySession(legacyHandle)

        val encrypted = symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)

        assertThat(symmetricKeyUtils.decrypt(encrypted)).isEqualTo(CryptoTestFixtures.SAMPLE_PLAINTEXT)
        // The same bytes the legacy Keystore handle would read, with no session at all.
        assertThat(AesGcmFieldCipher.decrypt(encrypted, legacyHandle))
            .isEqualTo(CryptoTestFixtures.SAMPLE_PLAINTEXT)
    }

    @Test
    fun decrypt_afterTheSessionKeyChanged_failsOnTheGcmTag() {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        val encryptedUnderLegacy = symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)
        sessionKeyHolder.setSessionKey(DEK_BYTES)

        assertThrows(GeneralSecurityException::class.java) {
            symmetricKeyUtils.decrypt(encryptedUnderLegacy)
        }
    }

    @Test
    fun decrypt_afterRelockAndReopenWithTheSameKey_returnsOriginalPlaintext() {
        sessionKeyHolder.setSessionKey(DEK_BYTES)
        val encrypted = symmetricKeyUtils.encrypt(CryptoTestFixtures.SAMPLE_PLAINTEXT)
        sessionKeyHolder.clearSessionKey()
        sessionKeyHolder.setSessionKey(DEK_BYTES)

        assertThat(symmetricKeyUtils.decrypt(encrypted)).isEqualTo(CryptoTestFixtures.SAMPLE_PLAINTEXT)
    }

    @Test
    fun constants_matchTheFieldCipherFormat() {
        assertThat(SymmetricKeyUtilsImpl.Constants.separator).isEqualTo(AesGcmFieldCipher.SEPARATOR)
        assertThat(SymmetricKeyUtilsImpl.Constants.gcmAuthTagLength)
            .isEqualTo(AesGcmFieldCipher.GCM_AUTH_TAG_LENGTH_BITS)
    }

    private companion object {
        val DEK_BYTES = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { (it * 3).toByte() }
    }
}
