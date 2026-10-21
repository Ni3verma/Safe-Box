package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.security.interfaces.CrashReporter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [CrashReporter] fake for instrumentation tests.
 */
@Singleton
class FakeCrashReporter @Inject constructor() : CrashReporter {
    private val _stringKeys = mutableMapOf<String, String>()
    val stringKeys: Map<String, String>
        get() = _stringKeys.toMap()

    private val _intKeys = mutableMapOf<String, Int>()
    val intKeys: Map<String, Int>
        get() = _intKeys.toMap()

    private val _breadcrumbs = mutableListOf<String>()
    val breadcrumbs: List<String>
        get() = _breadcrumbs.toList()

    private val _nonFatals = mutableListOf<Throwable>()
    val nonFatals: List<Throwable>
        get() = _nonFatals.toList()

    override fun setKey(key: String, value: String) {
        _stringKeys[key] = value
    }

    override fun setKey(key: String, value: Int) {
        _intKeys[key] = value
    }

    override fun breadcrumb(message: String) {
        _breadcrumbs.add(message)
    }

    override fun recordNonFatal(throwable: Throwable) {
        _nonFatals.add(throwable)
    }

    /**
     * Clears all recorded diagnostics.
     */
    fun reset() {
        _stringKeys.clear()
        _intKeys.clear()
        _breadcrumbs.clear()
        _nonFatals.clear()
    }
}
