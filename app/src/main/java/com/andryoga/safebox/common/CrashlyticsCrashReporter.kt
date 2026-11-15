package com.andryoga.safebox.common

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [CrashReporter] forwarding custom keys, breadcrumbs, and non-fatal exceptions to
 * [FirebaseCrashlytics].
 */
@Singleton
class CrashlyticsCrashReporter @Inject constructor() : CrashReporter {

    override fun setKey(key: String, value: String) {
        report { crashlytics -> crashlytics.setCustomKey(key, value) }
    }

    override fun setKey(key: String, value: Int) {
        report { crashlytics -> crashlytics.setCustomKey(key, value) }
    }

    override fun breadcrumb(message: String) {
        report { crashlytics -> crashlytics.log(message) }
    }

    override fun recordNonFatal(throwable: Throwable) {
        report { crashlytics -> crashlytics.recordException(throwable) }
    }

    /**
     * Runs [action] against the Crashlytics singleton and swallows every failure. Diagnostics must
     * never fail a vault operation, and this is the one place that cannot fall back to `Timber.e`
     * or [Utils.crashInDebugBuild]: `ReleaseTree` routes both through the same
     * `FirebaseCrashlytics.getInstance()` that just failed.
     */
    private inline fun report(action: (FirebaseCrashlytics) -> Unit) {
        try {
            action(FirebaseCrashlytics.getInstance())
        } catch (_: Exception) {
            // intentionally silent, see KDoc
        }
    }
}
