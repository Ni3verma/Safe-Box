package com.andryoga.safebox.security.envelope

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.common.Exceptions
import com.andryoga.safebox.security.interfaces.KeyHealth
import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.security.interfaces.WrongPasswordException
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class EnvelopeEncryptionManagerTest {

    private lateinit var fakeGateway: FakeKeystoreGateway
    private lateinit var manager: EnvelopeEncryptionManagerImpl

    @Before
    fun setUp() {
        fakeGateway = FakeKeystoreGateway()
        manager = EnvelopeEncryptionManagerImpl(fakeGateway)
    }

    @Test
    fun wrapAndUnwrapWithKeystoreKey_shouldRoundTripDekUsingExplicitMgf1Sha1Params() {
        val dek = manager.mintDek()
        val verifier = manager.makeVerifier(dek)

        for (spec in WrapKeySpec.entries) {
            val wrappedBlob = manager.wrapWithKeystoreKey(spec, dek)
            val cipher = manager.decryptCipherFor(spec)
            val unwrappedDek = manager.unwrapWithCipher(cipher, wrappedBlob)

            assertThat(unwrappedDek).isEqualTo(dek)
            assertThat(manager.verify(unwrappedDek, verifier)).isTrue()
        }
    }

    @Test
    fun rewrapPassword_shouldPreserveOriginalDekAndPassExistingVerifier() {
        val dek = manager.mintDek()
        val verifier = manager.makeVerifier(dek)
        val initialParams = testKdfParams(saltSeed = 1)
        val initialPassword = "First#Password12".toCharArray()
        val initialWrap = manager.wrapWithPassword(initialPassword, initialParams, dek)
        assertThat(initialPassword.all { it == '\u0000' }).isTrue()

        val unwrappedFirst = manager.unwrapWithPassword(
            password = "First#Password12".toCharArray(),
            params = initialParams,
            blob = initialWrap,
        ).getOrThrow()

        val updatedParams = testKdfParams(saltSeed = 2)
        val updatedWrap = manager.wrapWithPassword(
            password = "Second#Password34".toCharArray(),
            params = updatedParams,
            dek = unwrappedFirst,
        )
        val unwrappedSecond = manager.unwrapWithPassword(
            password = "Second#Password34".toCharArray(),
            params = updatedParams,
            blob = updatedWrap,
        ).getOrThrow()

        assertThat(unwrappedSecond).isEqualTo(dek)
        assertThat(manager.verify(unwrappedSecond, verifier)).isTrue()
    }

    @Test
    fun decryptCipherFor_whenKeyInvalidated_shouldThrowKeyPermanentlyInvalidatedAndRegenerateOnRewrap() {
        val dek = manager.mintDek()
        val biometricAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC)
        manager.wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, dek)
        assertThat(fakeGateway.probe(biometricAlias)).isEqualTo(KeyHealth.Healthy)
        assertThat(fakeGateway.generationCountFor(biometricAlias)).isEqualTo(1)

        fakeGateway.invalidateKey(biometricAlias)

        assertThat(fakeGateway.probe(biometricAlias)).isEqualTo(KeyHealth.Invalidated)
        assertThrows(KeyPermanentlyInvalidatedException::class.java) {
            manager.decryptCipherFor(WrapKeySpec.BIOMETRIC)
        }

        val rewrappedBlob = manager.wrapWithKeystoreKey(WrapKeySpec.BIOMETRIC, dek)
        assertThat(fakeGateway.probe(biometricAlias)).isEqualTo(KeyHealth.Healthy)
        assertThat(fakeGateway.generationCountFor(biometricAlias)).isEqualTo(2)
        val unwrapped = manager.unwrapWithCipher(
            cipher = manager.decryptCipherFor(WrapKeySpec.BIOMETRIC),
            blob = rewrappedBlob,
        )
        assertThat(unwrapped).isEqualTo(dek)
    }

    @Test
    fun unwrapWithPassword_withWrongPassword_shouldReturnFailureWithWrongPasswordException() {
        val dek = manager.mintDek()
        val params = testKdfParams(saltSeed = 5)
        val blob = manager.wrapWithPassword("Correct#Pass99".toCharArray(), params, dek)
        val wrongPassword = "Wrong#Password99".toCharArray()

        val result = manager.unwrapWithPassword(wrongPassword, params, blob)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(WrongPasswordException::class.java)
        assertThat(wrongPassword.all { it == '\u0000' }).isTrue()
    }

    @Test
    fun unwrapWithPassword_withMalformedBlob_shouldReturnFailureWithoutAsserting() {
        val params = testKdfParams(saltSeed = 6)

        val result = manager.unwrapWithPassword("Any#Password99".toCharArray(), params, "###not-base64###")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun unwrapWithPassword_withAuthenticBlobHoldingWrongSizedDek_shouldFailFastInDebugBuilds() {
        assumeTrue("crashInDebugBuild only throws on debug variants", BuildConfig.DEBUG)
        val params = testKdfParams(saltSeed = 7)
        val kek = PasswordKdf.deriveKey("Any#Password99".toCharArray(), params)
        // Same KEK and AAD as wrapWithPassword, but a 16-byte payload: the tag passes, the size check must not.
        val blob = AesGcmWrap.wrap(
            keyBytes = kek,
            plaintext = ByteArray(16),
            aad = "safebox.wrap.password.v1".toByteArray(Charsets.UTF_8),
        )

        assertThrows(Exceptions.DebugFatalException::class.java) {
            manager.unwrapWithPassword("Any#Password99".toCharArray(), params, blob)
        }
    }

    @Test
    fun wrapWithKeystoreKey_whenKeyPairAbsent_shouldGenerateKeyPairOnceAndReuseWhenHealthy() {
        val dek = manager.mintDek()
        val deviceAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE)
        assertThat(fakeGateway.probe(deviceAlias)).isEqualTo(KeyHealth.Missing)

        manager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, dek)
        assertThat(fakeGateway.probe(deviceAlias)).isEqualTo(KeyHealth.Healthy)
        assertThat(fakeGateway.generationCountFor(deviceAlias)).isEqualTo(1)

        manager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, dek)
        assertThat(fakeGateway.generationCountFor(deviceAlias)).isEqualTo(1)
    }

    @Test
    fun aliasFor_withSuffix_shouldAppendUnderscoreAndSuffix() {
        assertThat(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, null))
            .isEqualTo("safebox_kek_biometric")
        assertThat(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, ""))
            .isEqualTo("safebox_kek_biometric")
        assertThat(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.BIOMETRIC, "2"))
            .isEqualTo("safebox_kek_biometric_2")
        assertThat(EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE, "3"))
            .isEqualTo("safebox_kek_device_3")
    }

    @Test
    fun wrapAndDecryptCipherFor_withAliasSuffix_shouldUseSuffixedAliasInsteadOfDefault() {
        val dek = manager.mintDek()
        val defaultAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE)
        val suffixedAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE, "2")

        val wrappedBlob = manager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, dek, aliasSuffix = "2")

        assertThat(fakeGateway.probe(defaultAlias)).isEqualTo(KeyHealth.Missing)
        assertThat(fakeGateway.probe(suffixedAlias)).isEqualTo(KeyHealth.Healthy)

        val cipher = manager.decryptCipherFor(WrapKeySpec.DEVICE, aliasSuffix = "2")
        val unwrapped = manager.unwrapWithCipher(cipher, wrappedBlob)
        assertThat(unwrapped).isEqualTo(dek)
    }

    private fun testKdfParams(saltSeed: Int): KdfParams = KdfParams(
        algorithm = KdfParams.ALGORITHM_PBKDF2_SHA256,
        iterations = 1_000,
        salt = ByteArray(KdfParams.SALT_SIZE_BYTES) { index -> (saltSeed + index).toByte() },
    )
}
