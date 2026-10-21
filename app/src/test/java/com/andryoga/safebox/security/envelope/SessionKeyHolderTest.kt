package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.security.interfaces.SessionLockedException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class)
class SessionKeyHolderTest {

    private lateinit var holder: SessionKeyHolderImpl

    @Before
    fun setUp() {
        holder = SessionKeyHolderImpl()
    }

    @Test
    fun withSessionKey_whenLocked_shouldThrowSessionLockedException() {
        assertThat(holder.isUnlocked).isFalse()
        assertThat(holder.isLegacySession).isFalse()

        assertThrows(SessionLockedException::class.java) {
            holder.withSessionKey { it.encoded }
        }
    }

    @Test
    fun setSessionKey_shouldCopyInputAndSurviveCallerZeroingItsArray() {
        val callerDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { index -> (index + 1).toByte() }
        val expectedDek = callerDek.copyOf()

        holder.setSessionKey(callerDek)
        callerDek.fill(0)

        assertThat(holder.isUnlocked).isTrue()
        assertThat(holder.isLegacySession).isFalse()
        val keyBytesFromHolder = holder.withSessionKey { key -> key.encoded }
        assertThat(keyBytesFromHolder).isEqualTo(expectedDek)
    }

    @Test
    fun clearSessionKey_shouldZeroFillInternalBufferAndEmitLockEventOnce() = runTest {
        val dek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x5A }
        holder.setSessionKey(dek)
        val internalBuffer = checkNotNull(holder.peekInternalBufferForTesting())

        var lockEventCount = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            holder.lockEvents.collect { lockEventCount++ }
        }

        holder.clearSessionKey()

        assertThat(holder.isUnlocked).isFalse()
        assertThat(internalBuffer.all { it == 0.toByte() }).isTrue()
        assertThat(lockEventCount).isEqualTo(1)
        assertThrows(SessionLockedException::class.java) {
            holder.withSessionKey { }
        }
        job.cancel()
    }

    @Test
    fun setLegacySession_shouldExposeLegacyHandleAndMarkLegacySession() {
        val legacyHandle = SecretKeySpec(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x33 }, "AES")

        holder.setLegacySession(legacyHandle)

        assertThat(holder.isUnlocked).isTrue()
        assertThat(holder.isLegacySession).isTrue()
        val capturedKey = holder.withSessionKey { it }
        assertThat(capturedKey).isSameInstanceAs(legacyHandle)
    }

    @Test
    fun concurrentClearDuringWithSessionKey_shouldKeepInFlightKeyIntactAndLockHolder() {
        val dek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x7F }
        val expectedDek = dek.copyOf()
        holder.setSessionKey(dek)
        val internalBuffer = checkNotNull(holder.peekInternalBufferForTesting())

        val inFlightEncoded = holder.withSessionKey { inFlightKey ->
            holder.clearSessionKey()
            inFlightKey.encoded
        }

        assertThat(inFlightEncoded).isEqualTo(expectedDek)
        assertThat(internalBuffer.all { it == 0.toByte() }).isTrue()
        assertThat(holder.isUnlocked).isFalse()
        assertThrows(SessionLockedException::class.java) {
            holder.withSessionKey { }
        }
    }

    @Test
    fun setSessionKey_afterLegacySession_shouldReplaceStateWithStandardUnlockedSession() {
        val legacyHandle = SecretKeySpec(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x11 }, "AES")
        val newDek = ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 0x22 }
        holder.setLegacySession(legacyHandle)
        assertThat(holder.isLegacySession).isTrue()

        holder.setSessionKey(newDek)

        assertThat(holder.isUnlocked).isTrue()
        assertThat(holder.isLegacySession).isFalse()
        val activeBytes = holder.withSessionKey { it.encoded }
        assertThat(activeBytes).isEqualTo(newDek)
    }

    @Test
    fun setSessionKey_withInvalidLength_shouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            holder.setSessionKey(ByteArray(16))
        }
    }
}
