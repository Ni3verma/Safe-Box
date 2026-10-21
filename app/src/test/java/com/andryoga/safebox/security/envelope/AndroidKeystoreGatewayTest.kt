package com.andryoga.safebox.security.envelope

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.UnrecoverableKeyException
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.SecretKey

class AndroidKeystoreGatewayTest {

    private lateinit var mockKeyStore: KeyStore
    private lateinit var mockKeyPairGenerator: KeyPairGenerator
    private lateinit var mockCipher: Cipher
    private val recordedStrongBoxFlags = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        mockKeyStore = mockk(relaxed = true)
        mockKeyPairGenerator = mockk(relaxed = true)
        mockCipher = mockk(relaxed = true)
        recordedStrongBoxFlags.clear()
    }

    @Test
    fun generateWrapKeyPair_whenLockScreenNotConfigured_shouldThrowLockScreenRequiredException() {
        every { mockKeyPairGenerator.initialize(any<AlgorithmParameterSpec>()) } throws
            InvalidAlgorithmParameterException(
                IllegalStateException("Secure lock screen must be enabled to create keys requiring user authentication"),
            )
        val gateway = createGateway()

        assertThrows(LockScreenRequiredException::class.java) {
            gateway.generateWrapKeyPair("alias_device", WrapKeySpec.DEVICE)
        }
    }

    @Test
    fun generateWrapKeyPair_whenBiometricNotEnrolled_shouldThrowBiometricNotEnrolledException() {
        every { mockKeyPairGenerator.initialize(any<AlgorithmParameterSpec>()) } throws
            InvalidAlgorithmParameterException(
                IllegalStateException("At least one biometric must be enrolled to create keys requiring user authentication"),
            )
        val gateway = createGateway()

        assertThrows(BiometricNotEnrolledException::class.java) {
            gateway.generateWrapKeyPair("alias_bio", WrapKeySpec.BIOMETRIC)
        }
    }

    @Test
    fun generateWrapKeyPair_whenDirectIllegalStateExceptionThrown_shouldMapBySpecAndMessage() {
        every { mockKeyPairGenerator.generateKeyPair() } throws
            IllegalStateException("Secure lock screen must be enabled")
        val gateway = createGateway()

        assertThrows(LockScreenRequiredException::class.java) {
            gateway.generateWrapKeyPair("alias_bio", WrapKeySpec.BIOMETRIC)
        }
    }

    @Test
    fun generateWrapKeyPair_whenStrongBoxUnavailable_shouldRetryWithoutStrongBox() {
        val expectedPublicKey = mockk<PublicKey>()
        val keyPair = KeyPair(expectedPublicKey, mockk<PrivateKey>())
        var attempt = 0
        every { mockKeyPairGenerator.initialize(any<AlgorithmParameterSpec>()) } answers {
            attempt++
            if (attempt == 1) {
                throw StrongBoxUnavailableException("StrongBox not supported")
            }
        }
        every { mockKeyPairGenerator.generateKeyPair() } returns keyPair
        val gateway = createGateway()

        val result = gateway.generateWrapKeyPair("alias_device", WrapKeySpec.DEVICE)

        assertThat(result).isSameInstanceAs(expectedPublicKey)
        assertThat(recordedStrongBoxFlags).containsExactly(true, false).inOrder()
    }

    @Test
    fun probe_shouldMapKeystoreAndCipherOutcomesToKeyHealth() {
        val gateway = createGateway()
        val privateKey = mockk<PrivateKey>()

        // Missing alias -> KeyHealth.Missing
        every { mockKeyStore.getKey("missing", null) } returns null
        every { mockKeyStore.containsAlias("missing") } returns false
        assertThat(gateway.probe("missing")).isEqualTo(KeyHealth.Missing)

        // Null key while alias exists -> KeyHealth.Invalidated
        every { mockKeyStore.getKey("orphan", null) } returns null
        every { mockKeyStore.containsAlias("orphan") } returns true
        assertThat(gateway.probe("orphan")).isEqualTo(KeyHealth.Invalidated)

        // UnrecoverableKeyException (e.g. after lock screen removal) -> KeyHealth.Invalidated
        every { mockKeyStore.getKey("unrecoverable", null) } throws
            UnrecoverableKeyException("User deleted credentials")
        assertThat(gateway.probe("unrecoverable")).isEqualTo(KeyHealth.Invalidated)

        // Healthy per-operation key -> init returns normally
        every { mockKeyStore.getKey("healthy_per_op", null) } returns privateKey
        every { mockCipher.init(Cipher.DECRYPT_MODE, privateKey, AndroidKeystoreGateway.OAEP_SPEC) } just runs
        assertThat(gateway.probe("healthy_per_op")).isEqualTo(KeyHealth.Healthy)

        // Healthy time-bound key outside window -> init throws UserNotAuthenticatedException
        every { mockKeyStore.getKey("healthy_time_bound", null) } returns privateKey
        every { mockCipher.init(Cipher.DECRYPT_MODE, privateKey, AndroidKeystoreGateway.OAEP_SPEC) } throws
            UserNotAuthenticatedException()
        assertThat(gateway.probe("healthy_time_bound")).isEqualTo(KeyHealth.Healthy)

        // Invalidated key -> init throws KeyPermanentlyInvalidatedException
        every { mockKeyStore.getKey("invalidated", null) } returns privateKey
        every { mockCipher.init(Cipher.DECRYPT_MODE, privateKey, AndroidKeystoreGateway.OAEP_SPEC) } throws
            KeyPermanentlyInvalidatedException()
        assertThat(gateway.probe("invalidated")).isEqualTo(KeyHealth.Invalidated)
    }

    @Test
    fun legacyVaultKey_shouldReturnKeyOrNullWithoutRegenerating() {
        val gateway = createGateway()
        val expectedSecretKey = mockk<SecretKey>()

        every {
            mockKeyStore.getKey(AndroidKeystoreGateway.LEGACY_SYMMETRIC_KEY_ALIAS, null)
        } returns null
        assertThat(gateway.legacyVaultKey()).isNull()

        every {
            mockKeyStore.getKey(AndroidKeystoreGateway.LEGACY_SYMMETRIC_KEY_ALIAS, null)
        } returns expectedSecretKey
        assertThat(gateway.legacyVaultKey()).isSameInstanceAs(expectedSecretKey)
    }

    @Test
    fun deleteKey_whenDeleteEntryThrowsKeyStoreException_shouldNotPropagateException() {
        val gateway = createGateway()
        every { mockKeyStore.containsAlias("stuck_alias") } returns true
        every { mockKeyStore.deleteEntry("stuck_alias") } throws
            KeyStoreException("Failed to delete entry")

        gateway.deleteKey("stuck_alias")

        verify(exactly = 1) { mockKeyStore.deleteEntry("stuck_alias") }
    }

    private fun createGateway(): AndroidKeystoreGateway = AndroidKeystoreGateway(
        keyStoreProvider = { mockKeyStore },
        keyPairGeneratorProvider = { mockKeyPairGenerator },
        cipherProvider = { mockCipher },
        specFactory = { _, _, strongBoxBacked ->
            recordedStrongBoxFlags.add(strongBoxBacked)
            mockk<AlgorithmParameterSpec>()
        },
    )
}
