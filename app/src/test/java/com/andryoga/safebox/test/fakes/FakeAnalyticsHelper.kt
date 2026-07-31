package com.andryoga.safebox.test.fakes

import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.analytics.AnalyticsParamsBuilder
import com.andryoga.safebox.common.AnalyticsKey

data class LoggedAnalyticsEvent(
    val key: AnalyticsKey,
    val params: Map<String, Any> = emptyMap()
)

class FakeAnalyticsHelper : AnalyticsHelper {
    private val _loggedEvents = mutableListOf<LoggedAnalyticsEvent>()
    val loggedEvents: List<LoggedAnalyticsEvent> get() = _loggedEvents

    override fun logEvent(key: AnalyticsKey) {
        _loggedEvents.add(LoggedAnalyticsEvent(key))
    }

    override fun logEvent(key: AnalyticsKey, paramBlock: AnalyticsParamsBuilder.() -> Unit) {
        val builder = AnalyticsParamsBuilder().apply(paramBlock)
        _loggedEvents.add(LoggedAnalyticsEvent(key, builder.params.toMap()))
    }

    fun hasLogged(key: AnalyticsKey): Boolean {
        return _loggedEvents.any { it.key == key }
    }

    fun count(key: AnalyticsKey): Int {
        return _loggedEvents.count { it.key == key }
    }

    fun clear() {
        _loggedEvents.clear()
    }
}
