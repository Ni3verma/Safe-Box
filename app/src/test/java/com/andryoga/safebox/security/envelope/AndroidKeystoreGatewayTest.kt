package com.andryoga.safebox.security.envelope

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.StrongBoxUnavailableException
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.security.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException

class AndroidKeystoreGatewayTest {

    @Test
    fun mapKeyGenFailure_withLockScreenIllegalStateCause_shouldReturnLockScreenRequiredException() {
        val platformError = InvalidAlgorithmParameterException(
            IllegalStateException("Secure lock screen must be enabled to create keys requiring user authentication"),
        )

        val mapped = AndroidKeystoreGateway.mapKeyGenFailure(platformError, WrapKeySpec.DEVICE)

        assertThat(mapped).isInstanceOf(LockScreenRequiredException::class.java)
        assertThat(mapped.cause).isSameInstanceAs(platformError)
    }

    @Test
    fun mapKeyGenFailure_withBiometricIllegalStateCause_shouldReturnBiometricNotEnrolledException() {
        val platformError = InvalidAlgorithmParameterException(
            IllegalStateException("At least one biometric must be enrolled to create keys requiring user authentication"),
        )

        val mapped = AndroidKeystoreGateway.mapKeyGenFailure(platformError, WrapKeySpec.BIOMETRIC)

        assertThat(mapped).isInstanceOf(BiometricNotEnrolledException::class.java)
        assertThat(mapped.cause).isSameInstanceAs(platformError)
    }

    @Test
    fun mapKeyGenFailure_withDirectIllegalStateWithoutKeyword_shouldFallBackOnSpec() {
        val platformError = IllegalStateException("Keystore refused the request")

        val biometricMapped = AndroidKeystoreGateway.mapKeyGenFailure(platformError, WrapKeySpec.BIOMETRIC)
        val deviceMapped = AndroidKeystoreGateway.mapKeyGenFailure(platformError, WrapKeySpec.DEVICE)

        assertThat(biometricMapped).isInstanceOf(BiometricNotEnrolledException::class.java)
        assertThat(deviceMapped).isInstanceOf(LockScreenRequiredException::class.java)
    }

    @Test
    fun mapKeyGenFailure_withUnrelatedException_shouldReturnSameInstanceForCallerToHandle() {
        val unrelated = ProviderException("Keystore operation failed")

        val mapped = AndroidKeystoreGateway.mapKeyGenFailure(unrelated, WrapKeySpec.DEVICE)

        assertThat(mapped).isSameInstanceAs(unrelated)
    }

    @Test
    fun withStrongBoxFallback_whenStrongBoxUnavailable_shouldRetryOnceWithoutStrongBox() {
        val attempts = mutableListOf<Boolean>()

        val result = AndroidKeystoreGateway.withStrongBoxFallback { strongBoxBacked ->
            attempts.add(strongBoxBacked)
            if (strongBoxBacked) throw StrongBoxUnavailableException("StrongBox not supported")
            "generated"
        }

        assertThat(result).isEqualTo("generated")
        assertThat(attempts).containsExactly(true, false).inOrder()
    }

    @Test
    fun withStrongBoxFallback_whenStrongBoxUnavailableIsTheCause_shouldRetryOnceWithoutStrongBox() {
        val attempts = mutableListOf<Boolean>()

        AndroidKeystoreGateway.withStrongBoxFallback { strongBoxBacked ->
            attempts.add(strongBoxBacked)
            if (strongBoxBacked) {
                throw ProviderException("wrapped", StrongBoxUnavailableException("StrongBox not supported"))
            }
        }

        assertThat(attempts).containsExactly(true, false).inOrder()
    }

    @Test
    fun withStrongBoxFallback_whenOtherFailure_shouldNotRetryAndPropagate() {
        val attempts = mutableListOf<Boolean>()

        assertThrows(ProviderException::class.java) {
            AndroidKeystoreGateway.withStrongBoxFallback<Unit> { strongBoxBacked ->
                attempts.add(strongBoxBacked)
                throw ProviderException("TEE timeout")
            }
        }

        assertThat(attempts).containsExactly(true)
    }

    @Test
    fun healthFromFailure_withDocumentedKeystoreFailures_shouldReturnInvalidated() {
        val documentedFailures = listOf(
            UnrecoverableKeyException("User changed or deleted their auth credentials"),
            KeyStoreException("Failed to obtain information about key"),
            KeyPermanentlyInvalidatedException(),
            ProviderException("Keystore operation failed"),
        )

        documentedFailures.forEach { failure ->
            assertThat(AndroidKeystoreGateway.healthFromFailure(failure)).isEqualTo(KeyHealth.Invalidated)
        }
    }

    @Test
    fun healthFromFailure_withUnexpectedExceptionClass_shouldFailFastInDebugBuilds() {
        assumeTrue("crashInDebugBuild only throws on debug variants", BuildConfig.DEBUG)
        val programmingError = IllegalStateException("not a crypto failure")

        val thrown = assertThrows(Exceptions.DebugFatalException::class.java) {
            AndroidKeystoreGateway.healthFromFailure(programmingError)
        }

        assertThat(thrown.cause).isSameInstanceAs(programmingError)
    }
}
