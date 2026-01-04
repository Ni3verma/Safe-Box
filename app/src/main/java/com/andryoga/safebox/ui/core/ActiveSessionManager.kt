package com.andryoga.safebox.ui.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * This class manages the logout flow if user is away from the app for too long.
 * It also exposes a public API so that client's can pause/unpause the timer.
 * e.g. use-case: we don't want to auto logout the user when he is busy selecting a restore file.
 */
class ActiveSessionManager : DefaultLifecycleObserver {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var isPaused = false

    private val _logoutEvent = Channel<Unit>(Channel.CONFLATED)
    val logoutEvent = _logoutEvent.receiveAsFlow()

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
        timerJob = scope.launch {
            // wait for 10seconds before logging out. This will be coming from user pref in future
            delay(10.seconds)
            _logoutEvent.send(Unit)
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
}