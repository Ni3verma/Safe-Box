package com.andryoga.safebox.ui.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.AnalyticsParam
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Locks the vault when the user has been away from the app for too long.
 *
 * The timeout clears the session key; everything that follows — the HomeGraph gate leaving for
 * Login, the backup and restore work being cancelled — reacts to the holder, not to this class.
 * It also exposes a public API so that clients can pause/unpause the timer.
 * e.g. use-case: we don't want to auto logout the user when he is busy selecting a restore file.
 */
@Singleton
class ActiveSessionManager @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val settingsDataStore: SettingsDataStore,
    private val analyticsHelper: AnalyticsHelper,
    private val workManager: Lazy<WorkManager>,
    private val sessionKeyHolder: SessionKeyHolder,
    private val sessionLockReasonStore: SessionLockReasonStore,
) : DefaultLifecycleObserver {
    @Volatile
    private var timeout = SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT.seconds
    private var timerJob: Job? = null

    @Volatile
    private var isPaused = false

    init {
        applicationScope.launch {
            settingsDataStore.awayTimeoutSecFlow.collect {
                Timber.i("timeout updated in active session manager to $it seconds")
                timeout = it.seconds
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Timber.i("onStop")
        if (!isPaused) startLogoutTimer()
    }

    override fun onStart(owner: LifecycleOwner) {
        Timber.i("onStart")
        isPaused = false
        stopLogoutTimer()
    }

    private fun startLogoutTimer() {
        timerJob?.cancel()
        timerJob = applicationScope.launch {
            delay(timeout)
            // Once the delay has elapsed the lock is owed; a return to the foreground during the
            // short worker-state read below must not rescue the session.
            withContext(NonCancellable) { lockOpenSession() }
        }
    }

    /**
     * Clears an open session; a timeout that finds the vault already locked (the login screen was
     * showing) changes nothing, records no reason and logs nothing.
     */
    private suspend fun lockOpenSession() {
        if (!sessionKeyHolder.isUnlocked.value) return
        // Read before the clear: a running worker fails closed at its next use of the key, so a
        // read after it would mostly say idle.
        val workerRunning = isBackupOrRestoreRunning()
        Timber.i("away timeout: locking the vault")
        // Recorded before the clear: the gate navigates on the clear and Login reads it on arrival.
        sessionLockReasonStore.record(SessionLockReason.AWAY_TIMEOUT)
        sessionKeyHolder.clearSessionKey()
        analyticsHelper.logEvent(AnalyticsKey.SESSION_TIMEOUT) {
            param(AnalyticsParam.WORKER_RUNNING, workerRunning)
        }
    }

    private fun stopLogoutTimer() {
        timerJob?.cancel()
    }

    /**
     * public API to pause/unpause the timer. It is client's responsibility to
     * unpause the timer appropriately after pausing it.
     */
    fun setPaused(paused: Boolean) {
        Timber.i("setPaused: $paused")
        isPaused = paused
        if (paused) stopLogoutTimer()
    }

    /**
     * Telemetry only, run under [NonCancellable] by the timer: a failed or slow read counts as
     * idle, so it can never hold the lock back.
     */
    private suspend fun isBackupOrRestoreRunning(): Boolean {
        return try {
            withTimeoutOrNull(WORKER_STATE_READ_TIMEOUT) {
                val manager = workManager.get()
                listOf(
                    CommonConstants.WORKER_NAME_BACKUP_DATA,
                    CommonConstants.WORKER_NAME_RESTORE_DATA,
                ).any { uniqueWorkName ->
                    manager.getWorkInfosForUniqueWorkFlow(uniqueWorkName).first()
                        .any { it.state == WorkInfo.State.RUNNING }
                }
            } ?: false
        } catch (e: Exception) {
            Timber.w(e, "could not read worker state for session timeout")
            false
        }
    }

    private companion object {
        val WORKER_STATE_READ_TIMEOUT = 500.milliseconds
    }
}