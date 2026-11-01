package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.security.interfaces.WrapKeySpec
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import javax.crypto.BadPaddingException

/**
 * Unit tests for [WrapKeyOpener]: the probe-before-prompt and open-after-prompt outcomes over the
 * JCE-backed [FakeKeystoreGateway].
 */
class WrapKeyOpenerTest {

    private lateinit var keystoreGateway: FakeKeystoreGateway
    private lateinit var envelopeEncryptionManager: EnvelopeEncryptionManagerImpl
    private lateinit var opener: WrapKeyOpener

    private val testDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }
    private lateinit var verifier: String
    private lateinit var deviceBlob: String
    private val deviceAlias = EnvelopeEncryptionManagerImpl.aliasFor(WrapKeySpec.DEVICE)

    @Before
    fun setUp() {
        keystoreGateway = FakeKeystoreGateway()
        envelopeEncryptionManager = EnvelopeEncryptionManagerImpl(keystoreGateway)
        opener = WrapKeyOpener(envelopeEncryptionManager)
        verifier = envelopeEncryptionManager.makeVerifier(testDek)
        deviceBlob = envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, testDek)
    }

    @Test
    fun probe_whenKeyIsHealthy_shouldReturnReadyWithCipher() {
        val probe = opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null)

        assertThat(probe).isInstanceOf(WrapKeyProbe.Ready::class.java)
    }

    @Test
    fun probe_whenKeyIsTimeBoundAndWindowClosed_shouldReturnAwaitingAuthentication() {
        keystoreGateway.setRequireAuthOnInit(deviceAlias)

        val probe = opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null)

        assertThat(probe).isEqualTo(WrapKeyProbe.AwaitingAuthentication)
    }

    @Test
    fun probe_whenKeyIsInvalidated_shouldReturnInvalidated() {
        keystoreGateway.invalidateKey(deviceAlias)

        val probe = opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null)

        assertThat(probe).isEqualTo(WrapKeyProbe.Invalidated)
    }

    @Test
    fun probe_whenKeyWasNeverGenerated_shouldReturnInvalidated() {
        val probe = opener.probe(WrapKeySpec.BIOMETRIC, aliasSuffix = null)

        assertThat(probe).isEqualTo(WrapKeyProbe.Invalidated)
    }

    @Test
    fun open_withProbedCipher_shouldReturnOpenedDek() {
        val cipher = (opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null) as WrapKeyProbe.Ready).cipher

        val outcome = opener.open(cipher, WrapKeySpec.DEVICE, aliasSuffix = null, blob = deviceBlob, verifier = verifier)

        assertThat(outcome).isInstanceOf(WrapOpenOutcome.Opened::class.java)
        assertThat((outcome as WrapOpenOutcome.Opened).dek).isEqualTo(testDek)
    }

    @Test
    fun open_withNullCipherInsideAuthWindow_shouldInitCipherAndReturnOpenedDek() {
        val outcome = opener.open(cipher = null, WrapKeySpec.DEVICE, aliasSuffix = null, blob = deviceBlob, verifier = verifier)

        assertThat(outcome).isInstanceOf(WrapOpenOutcome.Opened::class.java)
        assertThat((outcome as WrapOpenOutcome.Opened).dek).isEqualTo(testDek)
    }

    @Test
    fun open_withNullCipherOutsideAuthWindow_shouldReturnNotAuthenticated() {
        keystoreGateway.setRequireAuthOnInit(deviceAlias)

        val outcome = opener.open(cipher = null, WrapKeySpec.DEVICE, aliasSuffix = null, blob = deviceBlob, verifier = verifier)

        assertThat(outcome).isEqualTo(WrapOpenOutcome.NotAuthenticated)
    }

    @Test
    fun open_whenBlobHoldsAnotherDek_shouldReturnWrongDek() {
        val otherDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x7A }
        val otherBlob = envelopeEncryptionManager.wrapWithKeystoreKey(WrapKeySpec.DEVICE, otherDek)
        val cipher = (opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null) as WrapKeyProbe.Ready).cipher

        val outcome = opener.open(cipher, WrapKeySpec.DEVICE, aliasSuffix = null, blob = otherBlob, verifier = verifier)

        assertThat(outcome).isEqualTo(WrapOpenOutcome.WrongDek)
    }

    @Test
    fun open_whenBlobIsTampered_shouldReturnKeystoreFailureWithCryptoCause() {
        val tampered = deviceBlob.toByteArray().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val cipher = (opener.probe(WrapKeySpec.DEVICE, aliasSuffix = null) as WrapKeyProbe.Ready).cipher

        val outcome = opener.open(cipher, WrapKeySpec.DEVICE, aliasSuffix = null, blob = String(tampered), verifier = verifier)

        assertThat(outcome).isInstanceOf(WrapOpenOutcome.KeystoreFailure::class.java)
        val cause = (outcome as WrapOpenOutcome.KeystoreFailure).cause
        assertThat(cause is BadPaddingException || cause is IllegalArgumentException).isTrue()
    }

    @Test
    fun open_withInvalidatedKeyAndNullCipher_shouldReturnKeystoreFailure() {
        keystoreGateway.invalidateKey(deviceAlias)

        val outcome = opener.open(cipher = null, WrapKeySpec.DEVICE, aliasSuffix = null, blob = deviceBlob, verifier = verifier)

        assertThat(outcome).isInstanceOf(WrapOpenOutcome.KeystoreFailure::class.java)
    }
}
