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
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @MockK
    lateinit var workManager: WorkManager

    private val analyticsHelper = FakeAnalyticsHelper()
    private val lifecycleOwner = mockk<LifecycleOwner>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { settingsDataStore.awayTimeoutSecFlow } returns flowOf(TIMEOUT_SEC)
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
    }

    @Test
    fun onStop_afterTimeout_shouldEmitLogoutThenLogSessionTimeoutWithNoWorkerRunning() = runTest {
        val manager = createManager()
        val logouts = collectLogouts(manager)

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(logouts).hasSize(1)
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.SESSION_TIMEOUT)
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
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

        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.SESSION_TIMEOUT)
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

        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
    }

    @Test
    fun onStop_whenWorkManagerThrows_shouldStillLogoutAndLogSessionTimeout() = runTest {
        every { workManager.getWorkInfosForUniqueWorkFlow(any()) } throws
            IllegalStateException("WorkManager is not initialized properly")
        val manager = createManager()
        val logouts = collectLogouts(manager)

        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(logouts).hasSize(1)
        val event = analyticsHelper.loggedEvents.single()
        assertThat(event.key).isEqualTo(AnalyticsKey.SESSION_TIMEOUT)
        assertThat(event.params[AnalyticsParam.WORKER_RUNNING.paramName]).isEqualTo(false)
    }

    @Test
    fun onStart_beforeTimeout_shouldNeitherLogoutNorLogSessionTimeout() = runTest {
        val manager = createManager()
        val logouts = collectLogouts(manager)

        manager.onStop(lifecycleOwner)
        advanceTimeBy((TIMEOUT_SEC - 1).seconds)
        manager.onStart(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(logouts).isEmpty()
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    @Test
    fun onStop_whilePaused_shouldNeitherLogoutNorLogSessionTimeout() = runTest {
        val manager = createManager()
        val logouts = collectLogouts(manager)

        manager.setPaused(true)
        manager.onStop(lifecycleOwner)
        advanceTimeBy(TIMEOUT_SEC.seconds)
        runCurrent()

        assertThat(logouts).isEmpty()
        assertThat(analyticsHelper.loggedEvents).isEmpty()
    }

    /** [backgroundScope] stands in for the application scope so the timer runs on the test scheduler. */
    private fun TestScope.createManager(): ActiveSessionManager {
        val manager = ActiveSessionManager(
            applicationScope = backgroundScope,
            settingsDataStore = settingsDataStore,
            analyticsHelper = analyticsHelper,
            workManager = Lazy { workManager },
        )
        // lets init consume the timeout setting before any test drives the lifecycle
        runCurrent()
        return manager
    }

    private fun TestScope.collectLogouts(manager: ActiveSessionManager): List<Unit> {
        val logouts = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.logoutEvent.collect { logouts.add(it) }
        }
        runCurrent()
        return logouts
    }

    private companion object {
        const val TIMEOUT_SEC = 7
    }
}
