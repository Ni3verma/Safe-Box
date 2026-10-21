package com.andryoga.safebox.security.envelope

import com.andryoga.safebox.security.interfaces.CrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [CrashReporter] forwarding custom keys, breadcrumbs, and non-fatal exceptions to
 * [FirebaseCrashlytics].
 *
 * Every call is guarded so an uninitialized or unavailable Crashlytics instance never disrupts a
 * vault operation.
 */
@Singleton
class CrashlyticsCrashReporter @Inject constructor() : CrashReporter {

    override fun setKey(key: String, value: String) {
        runSafely { setCustomKey(key, value) }
    }

    override fun setKey(key: String, value: Int) {
        runSafely { setCustomKey(key, value) }
    }

    override fun breadcrumb(message: String) {
        runSafely { log(message) }
    }

    override fun recordNonFatal(throwable: Throwable) {
        runSafely { recordException(throwable) }
    }

    private inline fun runSafely(block: FirebaseCrashlytics.() -> Unit) {
        try {
            FirebaseCrashlytics.getInstance().block()
        } catch (_: RuntimeException) {
            // Diagnostics must never fail the caller when Crashlytics is unavailable.
        }
    }
}
