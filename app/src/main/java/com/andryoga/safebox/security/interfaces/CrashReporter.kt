package com.andryoga.safebox.security.interfaces

/**
 * Diagnostics seam for recording structured non-PII custom keys, breadcrumbs, and non-fatal
 * exceptions to the crash reporting backend.
 */
interface CrashReporter {
    fun setKey(key: String, value: String)
    fun setKey(key: String, value: Int)
    fun breadcrumb(message: String)
    fun recordNonFatal(throwable: Throwable)
}
