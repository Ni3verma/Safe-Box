package com.andryoga.safebox.security

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException

class DecryptFailureReportingSymmetricKeyUtilsTest {

    @MockK
    lateinit var delegate: SymmetricKeyUtils

    private val analyticsHelper = FakeAnalyticsHelper()
    private lateinit var subject: DecryptFailureReportingSymmetricKeyUtils

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        subject = DecryptFailureReportingSymmetricKeyUtils(delegate, analyticsHelper)
    }

    @Test
    fun encrypt_shouldForwardToDelegateWithoutLogging() {
        every { delegate.encrypt("plain") } returns "cipher"

        assertThat(subject.encrypt("plain")).isEqualTo("cipher")
        verify(exactly = 1) { delegate.encrypt("plain") }
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun decrypt_whenDelegateSucceeds_shouldReturnPlaintextWithoutLogging() {
        every { delegate.decrypt("cipher") } returns "plain"

        assertThat(subject.decrypt("cipher")).isEqualTo("plain")
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun decrypt_whenDelegateThrows_shouldRethrowSameInstanceAndLogExceptionClass() {
        val failure = AEADBadTagException("tag mismatch")
        every { delegate.decrypt("cipher") } throws failure

        val thrown = assertThrows(AEADBadTagException::class.java) { subject.decrypt("cipher") }

        assertThat(thrown).isSameInstanceAs(failure)
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.RECORD_DECRYPT_FAILURE)
        assertThat(event.params[AnalyticsParam.EXCEPTION.paramName]).isEqualTo("AEADBadTagException")
    }

    @Test
    fun decrypt_whenSameExceptionClassRepeats_shouldLogOnlyOnce() {
        every { delegate.decrypt(any()) } throws AEADBadTagException("tag mismatch")

        repeat(5) { assertThrows(AEADBadTagException::class.java) { subject.decrypt("row$it") } }

        assertThat(analyticsHelper.count(AnalyticsKey.RECORD_DECRYPT_FAILURE)).isEqualTo(1)
    }

    @Test
    fun decrypt_whenDifferentExceptionClassesOccur_shouldLogEachClassOnce() {
        every { delegate.decrypt("a") } throws AEADBadTagException("tag mismatch")
        every { delegate.decrypt("b") } throws IllegalBlockSizeException("short input")

        assertThrows(AEADBadTagException::class.java) { subject.decrypt("a") }
        assertThrows(IllegalBlockSizeException::class.java) { subject.decrypt("b") }
        assertThrows(AEADBadTagException::class.java) { subject.decrypt("a") }

        val loggedClasses = analyticsHelper.loggedEvents
            .filter { it.key == AnalyticsKey.RECORD_DECRYPT_FAILURE }
            .map { it.params[AnalyticsParam.EXCEPTION.paramName] }
        assertThat(loggedClasses).containsExactly("AEADBadTagException", "IllegalBlockSizeException")
    }

    @Test
    fun decrypt_whenDelegateThrows_shouldNotLeakCiphertextIntoEventParams() {
        every { delegate.decrypt("very-secret-ciphertext") } throws AEADBadTagException("bad")

        assertThrows(AEADBadTagException::class.java) { subject.decrypt("very-secret-ciphertext") }

        val params = analyticsHelper.loggedEvents.single().params
        assertThat(params.keys).containsExactly(AnalyticsParam.EXCEPTION.paramName)
        assertThat(params.values.map { it.toString() }).doesNotContain("very-secret-ciphertext")
    }

    @Test
    fun decrypt_whenReporterThrows_shouldStillRethrowTheDecryptionException() {
        val throwingAnalytics = mockk<AnalyticsHelper>()
        every { throwingAnalytics.logEvent(any(), any()) } throws IllegalStateException("Firebase not initialised")
        val failure = AEADBadTagException("tag mismatch")
        every { delegate.decrypt("cipher") } throws failure
        val subject = DecryptFailureReportingSymmetricKeyUtils(delegate, throwingAnalytics)

        val thrown = assertThrows(AEADBadTagException::class.java) { subject.decrypt("cipher") }

        assertThat(thrown).isSameInstanceAs(failure)
    }

    @Test
    fun decrypt_whenVaultIsLocked_shouldRethrowWithoutReporting() {
        val locked = SessionLockedException()
        every { delegate.decrypt("cipher") } throws locked

        val thrown = assertThrows(SessionLockedException::class.java) { subject.decrypt("cipher") }

        assertThat(thrown).isSameInstanceAs(locked)
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }
}
