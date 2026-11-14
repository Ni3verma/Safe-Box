package com.andryoga.safebox.common

/**
 * Diagnostics seam for recording structured non-PII custom keys, breadcrumbs, and non-fatal
 * exceptions to the crash reporting backend.
 *
 * Lives beside [CrashlyticsKeys] rather than under `security/`: it is the app's crash-reporting
 * boundary, which the vault code merely consumes. Production binds [CrashlyticsCrashReporter];
 * tests bind a fake so diagnostics are asserted instead of hoped for.
 */
interface CrashReporter {
    fun setKey(key: String, value: String)
    fun setKey(key: String, value: Int)
    fun breadcrumb(message: String)
    fun recordNonFatal(throwable: Throwable)
}
