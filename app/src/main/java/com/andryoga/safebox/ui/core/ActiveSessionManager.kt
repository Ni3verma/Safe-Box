package com.andryoga.safebox.ui.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.security.interfaces.SessionKeyHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
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
    private val sessionKeyHolder: SessionKeyHolder,
    private val sessionLockReasonStore: SessionLockReasonStore,
) : DefaultLifecycleObserver {
    private var timerJob: Job? = null

    @Volatile
    private var isPaused = false

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
            // Read at stop time, not cached: the value the user just saved in Settings is the one
            // that applies, and there is no collector whose delivery the lock could race.
            val timeout = settingsDataStore.awayTimeoutSecFlow.first().seconds
            Timber.i("away timeout armed for $timeout")
            delay(timeout)
            lockOpenSession()
        }
    }

    /**
     * Clears an open session; a timeout that finds the vault already locked (the login screen was
     * showing) changes nothing, records no reason and logs nothing.
     */
    private fun lockOpenSession() {
        if (!sessionKeyHolder.isUnlocked.value) return
        Timber.i("away timeout: locking the vault")
        // Recorded before the clear: the gate navigates on the clear and Login reads it on arrival.
        sessionLockReasonStore.record(SessionLockReason.AWAY_TIMEOUT)
        sessionKeyHolder.clearSessionKey()
        analyticsHelper.logEvent(AnalyticsKey.SESSION_TIMEOUT)
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
}
