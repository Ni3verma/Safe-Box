package com.andryoga.safebox.security.envelope

import android.app.KeyguardManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.security.envelope.interfaces.BiometricNotEnrolledException
import com.andryoga.safebox.security.envelope.interfaces.LockScreenRequiredException
import com.andryoga.safebox.security.envelope.models.KeyHealth
import com.andryoga.safebox.security.envelope.models.WrapKeySpec
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * The real [AndroidKeystoreGateway] against Android Keystore: the key spec, the probe and the OAEP
 * parameters that the JVM suite can only check as decisions.
 *
 * Two halves. The first runs on any image, including the CI `aosp-atd` one, which has no secure
 * lock screen: the refusal is mapped, absent aliases are handled, and a JCE-wrapped blob opens under
 * a Keystore private key. The second needs a PIN on the device (`adb shell locksettings set-pin`,
 * `google_apis` image) and is skipped elsewhere: it generates the real wrap keys and proves the
 * auth binding refuses an unauthenticated unwrap. A successful authenticated unwrap needs a person
 * at the prompt and stays in the manual matrix.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreWrapKeysTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val gateway = AndroidKeystoreGateway()
    private val isDeviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    @After
    fun tearDown() {
        listOf(DEVICE_ALIAS, BIOMETRIC_ALIAS, INTEROP_ALIAS, MISSING_ALIAS).forEach(gateway::deleteKey)
    }

    @Test
    fun generateDeviceWrapKey_withoutASecureLockScreen_shouldThrowLockScreenRequired() {
        assumeFalse("device has a secure lock screen", isDeviceSecure)

        val error = runCatching { gateway.generateWrapKeyPair(DEVICE_ALIAS, WrapKeySpec.DEVICE) }.exceptionOrNull()

        assertThat(error).isInstanceOf(LockScreenRequiredException::class.java)
        assertThat(gateway.probe(DEVICE_ALIAS)).isEqualTo(KeyHealth.Missing)
    }

    @Test
    fun probeAndDelete_forAnAliasThatWasNeverCreated_shouldReportMissingWithoutThrowing() {
        assertThat(gateway.probe(MISSING_ALIAS)).isEqualTo(KeyHealth.Missing)
        assertThat(gateway.publicKey(MISSING_ALIAS)).isNull()
        gateway.deleteKey(MISSING_ALIAS)
        assertThat(gateway.probe(MISSING_ALIAS)).isEqualTo(KeyHealth.Missing)
    }

    @Test
    fun oaepInterop_aJceWrappedBlob_shouldOpenUnderAKeystorePrivateKeyWithTheGatewayParameters() {
        // Same padding and digests as the wrap keys, minus the auth binding the CI image cannot create.
        val keystorePublic = generateUnboundKeystoreRsaKey(INTEROP_ALIAS)
        val privateKey = keyStore().getKey(INTEROP_ALIAS, null) as PrivateKey
        val dek = ByteArray(32) { it.toByte() }

        val blob = jceEncrypt(keystorePublic, dek, explicitOaepSpec = true)
        val opened = Cipher.getInstance(AndroidKeystoreGateway.RSA_OAEP_TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, privateKey, AndroidKeystoreGateway.OAEP_SPEC)
            doFinal(blob)
        }

        assertThat(opened).isEqualTo(dek)
    }

    @Test
    fun oaepInterop_aJceBlobWithProviderDefaultParameters_shouldNotOpenUnderTheKeystoreKey() {
        val keystorePublic = generateUnboundKeystoreRsaKey(INTEROP_ALIAS)
        val privateKey = keyStore().getKey(INTEROP_ALIAS, null) as PrivateKey

        // Conscrypt defaults MGF1 to SHA-256 while Keystore uses SHA-1: why both sides pin OAEP_SPEC.
        val blob = jceEncrypt(keystorePublic, ByteArray(32), explicitOaepSpec = false)
        val result = runCatching {
            Cipher.getInstance(AndroidKeystoreGateway.RSA_OAEP_TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, privateKey, AndroidKeystoreGateway.OAEP_SPEC)
                doFinal(blob)
            }
        }

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun generateDeviceWrapKey_onASecuredDevice_shouldBeHealthyAndRefuseAnUnauthenticatedUnwrap() {
        assumeTrue("needs a secure lock screen (google_apis image + locksettings set-pin)", isDeviceSecure)

        val publicKey = gateway.generateWrapKeyPair(DEVICE_ALIAS, WrapKeySpec.DEVICE)

        assertThat(gateway.publicKey(DEVICE_ALIAS)).isNotNull()
        assertThat(gateway.probe(DEVICE_ALIAS)).isEqualTo(KeyHealth.Healthy)
        val blob = jceEncrypt(publicKey, ByteArray(32) { 7 }, explicitOaepSpec = true)
        // API 30+: per-operation key, init passes and doFinal is refused; API 29: time-bound, init is refused.
        val unwrap = runCatching { gateway.decryptCipher(DEVICE_ALIAS).doFinal(blob) }
        assertThat(unwrap.isFailure).isTrue()

        gateway.deleteKey(DEVICE_ALIAS)
        assertThat(gateway.probe(DEVICE_ALIAS)).isEqualTo(KeyHealth.Missing)
    }

    @Test
    fun generateBiometricWrapKey_onASecuredDevice_shouldFollowWhetherAStrongBiometricIsEnrolled() {
        assumeTrue("needs a secure lock screen (google_apis image + locksettings set-pin)", isDeviceSecure)
        val hasStrongBiometric =
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        val result = runCatching { gateway.generateWrapKeyPair(BIOMETRIC_ALIAS, WrapKeySpec.BIOMETRIC) }

        if (hasStrongBiometric) {
            assertThat(result.isSuccess).isTrue()
            assertThat(gateway.probe(BIOMETRIC_ALIAS)).isEqualTo(KeyHealth.Healthy)
        } else {
            assertThat(result.exceptionOrNull()).isInstanceOf(BiometricNotEnrolledException::class.java)
            assertThat(gateway.probe(BIOMETRIC_ALIAS)).isEqualTo(KeyHealth.Missing)
        }
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(AndroidKeystoreGateway.ANDROID_KEYSTORE).apply { load(null) }

    private fun generateUnboundKeystoreRsaKey(alias: String): PublicKey {
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            AndroidKeystoreGateway.ANDROID_KEYSTORE,
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(AndroidKeystoreGateway.RSA_KEY_SIZE_BITS)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .build(),
        )
        return generator.generateKeyPair().public
    }

    /**
     * Encrypts with the default JCA provider on a re-encoded copy of [publicKey], as
     * `EnvelopeEncryptionManagerImpl.wrapWithKeystoreKey` does, so Keystore takes no part.
     */
    private fun jceEncrypt(publicKey: PublicKey, data: ByteArray, explicitOaepSpec: Boolean): ByteArray {
        val jcaKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey.encoded))
        return Cipher.getInstance(AndroidKeystoreGateway.RSA_OAEP_TRANSFORMATION).run {
            if (explicitOaepSpec) {
                init(Cipher.ENCRYPT_MODE, jcaKey, AndroidKeystoreGateway.OAEP_SPEC)
            } else {
                init(Cipher.ENCRYPT_MODE, jcaKey)
            }
            doFinal(data)
        }
    }

    private companion object {
        const val DEVICE_ALIAS = "keystore_wrap_keys_test_device"
        const val BIOMETRIC_ALIAS = "keystore_wrap_keys_test_biometric"
        const val INTEROP_ALIAS = "keystore_wrap_keys_test_interop"
        const val MISSING_ALIAS = "keystore_wrap_keys_test_missing"
    }
}
