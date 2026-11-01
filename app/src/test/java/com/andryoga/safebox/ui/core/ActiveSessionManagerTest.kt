@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.core

import androidx.lifecycle.LifecycleOwner
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ActiveSessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    private val analyticsHelper = FakeAnalyticsHelper()
    private val lifecycleOwner = mockk<LifecycleOwner>()
    private val sessionKeyHolder = SessionKeyHolderImpl()
    private val sessionLockReasonStore = SessionLockReasonStore()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.awayTimeoutSecFlow } returns flowOf(TIMEOUT_SEC)
        sessionKeyHolder.setSessionKey(ByteArray(AesGcmWrap.KEY_SIZE_BYTES) { 1 })
    }

    @Test
    fun onStop_shouldArmTheTimeoutSavedAfterConstruction() = runTest {
        val timeoutSec = MutableStateFlow(TIMEOUT_SEC)
        every { settingsDataStore.awayTimeoutSecFlow } returns timeoutSec
        val manager = createManager()
        timeoutSec.value = 0

        manager.onStop(lifecycleOwner)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
    }

    @Test
    fun onStop_afterTimeout_shouldClearTheSessionKeyAndRecordTheAwayReason() = runTest {
        val manager = createManager()
        val locks = collectLockEvents()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(locks).hasSize(1)
        assertThat(sessionLockReasonStore.consume()).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
    }

    @Test
    fun onStop_afterTimeout_shouldLogSessionTimeoutOnce() = runTest {
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(analyticsHelper.count(AnalyticsKey.SESSION_TIMEOUT)).isEqualTo(1)
    }

    @Test
    fun onStop_afterTimeout_shouldRecordTheReasonBeforeClearingTheKey() = runTest {
        // The gate navigates on the clear and Login reads the reason on arrival, so the reason
        // must already be there when the holder reports locked.
        var reasonAtLock: SessionLockReason? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionKeyHolder.lockEvents.collect { reasonAtLock = sessionLockReasonStore.consume() }
        }
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(reasonAtLock).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
    }

    @Test
    fun onStop_afterTimeoutInALegacySession_shouldClearTheSessionAndRecordTheAwayReason() = runTest {
        sessionKeyHolder.setLegacySession(CryptoTestFixtures.TEST_AES_KEY_256)
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(sessionKeyHolder.isLegacySession).isFalse()
        assertThat(sessionLockReasonStore.consume()).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
        assertThat(analyticsHelper.count(AnalyticsKey.SESSION_TIMEOUT)).isEqualTo(1)
    }

    @Test
    fun onStop_afterTimeoutWhileAlreadyLocked_shouldRecordNoReasonAndLogNothing() = runTest {
        // The login screen was showing: nothing to lock, and a stale reason must not greet the
        // login after the next session.
        sessionKeyHolder.clearSessionKey()
        val manager = createManager()
        val locks = collectLockEvents()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(locks).isEmpty()
        assertThat(sessionLockReasonStore.consume()).isNull()
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun onStart_beforeTimeout_shouldNeitherLockNorLogSessionTimeout() = runTest {
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy((TIMEOUT_SEC - 1).seconds)
        manager.onStart(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
        assertThat(sessionLockReasonStore.consume()).isNull()
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun onStop_whilePaused_shouldNeitherLockNorLogSessionTimeout() = runTest {
        val manager = createManager()

        manager.setPaused(true)
        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isTrue()
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    /** [backgroundScope] stands in for the application scope so the timer runs on the test scheduler. */
    private fun TestScope.createManager(): ActiveSessionManager {
        val manager = ActiveSessionManager(
            applicationScope = backgroundScope,
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
            sessionKeyHolder = sessionKeyHolder,
            sessionLockReasonStore = sessionLockReasonStore,
        )
        runCurrent()
        return manager
    }

    private fun TestScope.collectLockEvents(): List<Unit> {
        val locks = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionKeyHolder.lockEvents.collect { locks.add(it) }
        }
        runCurrent()
        return locks
    }

    private companion object {
        const val TIMEOUT_SEC = 7
    }
}
