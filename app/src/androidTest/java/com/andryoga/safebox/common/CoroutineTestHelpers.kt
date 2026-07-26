package com.andryoga.safebox.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * Coroutine time advancement helpers for unit and instrumented tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object CoroutineTestHelpers {
    /**
     * Advances virtual time cleanly by [timeoutSec] seconds on [testScheduler]
     * to trigger time-based timeouts without thread sleeping or manual lifecycle invocation.
     */
    fun advanceActiveSessionTimeout(testScheduler: TestCoroutineScheduler, timeoutSec: Int) {
        testScheduler.advanceTimeBy(timeoutSec * 1000L)
        testScheduler.runCurrent()
    }
}
