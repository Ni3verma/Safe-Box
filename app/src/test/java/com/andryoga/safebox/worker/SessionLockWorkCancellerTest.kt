@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.worker

import androidx.work.WorkManager
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SessionLockWorkCancellerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val workManager: WorkManager = mockk(relaxed = true)
    private val sessionKeyHolder = SessionKeyHolderImpl()
    private val registry = BackupPasswordRegistry()

    @Test
    fun lock_shouldCancelBothUniqueWorks() = runTest {
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 1 })
        startCanceller()

        sessionKeyHolder.clearSessionKey()
        runCurrent()

        verify(exactly = 1) { workManager.cancelUniqueWork(CommonConstants.WORKER_NAME_BACKUP_DATA) }
        verify(exactly = 1) { workManager.cancelUniqueWork(CommonConstants.WORKER_NAME_RESTORE_DATA) }
    }

    @Test
    fun lock_inALegacySession_shouldDropEveryRegisteredPassword() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        val backupId = UUID.randomUUID()
        val restoreId = UUID.randomUUID()
        registry.put(backupId, "Qwerty@@123")
        registry.put(restoreId, "Other#45")
        startCanceller()

        sessionKeyHolder.clearSessionKey()
        runCurrent()

        assertThat(registry.take(backupId)).isNull()
        assertThat(registry.take(restoreId)).isNull()
    }

    @Test
    fun noLock_shouldCancelNothing() = runTest {
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 1 })
        registry.put(UUID.randomUUID(), "Qwerty@@123")
        startCanceller()
        runCurrent()

        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun lock_beforeStart_shouldCancelNothing() = runTest {
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 1 })
        createCanceller()

        sessionKeyHolder.clearSessionKey()
        runCurrent()

        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    private fun TestScope.startCanceller() {
        createCanceller().start()
        runCurrent()
    }

    /** [backgroundScope] stands in for the application scope so the subscription runs on the test scheduler. */
    private fun TestScope.createCanceller() = SessionLockWorkCanceller(
        applicationScope = backgroundScope,
        sessionKeyHolder = sessionKeyHolder,
        workManager = Lazy { workManager },
        backupPasswordRegistry = registry,
    )
}
