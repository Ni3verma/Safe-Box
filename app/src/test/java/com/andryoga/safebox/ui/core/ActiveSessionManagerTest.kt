@file:OptIn(ExperimentalCoroutinesApi::class)

package com.andryoga.safebox.ui.core

import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.security.CryptoTestFixtures
import com.andryoga.safebox.security.envelope.AesGcmWrap
import com.andryoga.safebox.security.envelope.SessionKeyHolderImpl
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ActiveSessionManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var settingsDataStore: SettingsDataStore

    @MockK
    lateinit var workManager: WorkManager

    private val analyticsHelper = FakeAnalyticsHelper()
    private val lifecycleOwner = mockk<LifecycleOwner>()
    private val sessionKeyHolder = SessionKeyHolderImpl()
    private val sessionLockReasonStore = SessionLockReasonStore()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.awayTimeoutSecFlow } returns flowOf(TIMEOUT_SEC)
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
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
    fun onStop_afterTimeout_shouldLogSessionTimeoutOnceWithNoWorkerRunning() = runTest {
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        val timeout = analyticsHelper.loggedEvents.single { it.key == AnalyticsKey.SESSION_TIMEOUT }
        assertThat(timeout.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
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
    fun onStop_whenRestoreWorkerIsRunning_shouldLogSessionTimeoutWithWorkerRunning() = runTest {
        val running = mockk<WorkInfo> { every { state } returns WorkInfo.State.RUNNING }
        every {
            workManager.getWorkInfosForUniqueWorkFlow(CommonConstants.WORKER_NAME_RESTORE_DATA)
        } returns flowOf(listOf(running))
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        val event = analyticsHelper.loggedEvents.single { it.key == AnalyticsKey.SESSION_TIMEOUT }
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(true)
    }

    @Test
    fun onStop_whenBackupWorkerHasFinished_shouldLogSessionTimeoutWithNoWorkerRunning() = runTest {
        val finished = mockk<WorkInfo> { every { state } returns WorkInfo.State.SUCCEEDED }
        every {
            workManager.getWorkInfosForUniqueWorkFlow(CommonConstants.WORKER_NAME_BACKUP_DATA)
        } returns flowOf(listOf(finished))
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        val event = analyticsHelper.loggedEvents.single { it.key == AnalyticsKey.SESSION_TIMEOUT }
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
    }

    @Test
    fun onStop_whenWorkManagerThrows_shouldStillLockAndLogSessionTimeout() = runTest {
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } throws
            IllegalStateException("WorkManager is not initialized properly")
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        val event = analyticsHelper.loggedEvents.single { it.key == AnalyticsKey.SESSION_TIMEOUT }
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
    }

    @Test
    fun onStop_whenTheWorkerStateReadHangs_shouldLockAtOnceAndLogOnceTheReadBoundElapses() = runTest {
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns MutableSharedFlow()
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()
        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(analyticsHelper.loggedEvents.none { it.key == AnalyticsKey.SESSION_TIMEOUT }).isTrue()
        advanceTimeBy(READ_BOUND)
        runCurrent()

        val event = analyticsHelper.loggedEvents.single { it.key == AnalyticsKey.SESSION_TIMEOUT }
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
    }

    @Test
    fun onStart_duringTheWorkerStateRead_shouldNotRescueTheSession() = runTest {
        // The timer has elapsed: a foreground return inside the telemetry read must not undo the lock.
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns MutableSharedFlow()
        val manager = createManager()

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()
        manager.onStart(lifecycleOwner)
        advanceTimeBy(READ_BOUND)
        runCurrent()

        assertThat(sessionKeyHolder.isUnlocked.value).isFalse()
        assertThat(sessionLockReasonStore.consume()).isEqualTo(SessionLockReason.AWAY_TIMEOUT)
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
            workManager = Lazy { workManager },
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
        val READ_BOUND = 500.milliseconds
    }
}
