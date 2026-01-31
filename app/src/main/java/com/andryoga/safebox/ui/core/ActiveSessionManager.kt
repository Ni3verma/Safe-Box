package com.andryoga.safebox.ui.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * This class manages the logout flow if user is away from the app for too long.
 * It also exposes a public API so that client's can pause/unpause the timer.
 * e.g. use-case: we don't want to auto logout the user when he is busy selecting a restore file.
 */
@Singleton
class ActiveSessionManager @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val settingsDataStore: SettingsDataStore,
) : DefaultLifecycleObserver {
    private var timeout = SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT.seconds
    private var timerJob: Job? = null
    private var isPaused = false

    private val _logoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvent = _logoutEvent.asSharedFlow()

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
            Timber.i("emitting logout event")
            _logoutEvent.emit(Unit)
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