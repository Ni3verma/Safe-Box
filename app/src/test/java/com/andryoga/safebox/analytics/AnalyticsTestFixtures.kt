package com.andryoga.safebox.analytics

import com.andryoga.safebox.common.AnalyticsKey

data class LoggedAnalyticsEvent(
    val key: AnalyticsKey,
    val params: Map<String, Any> = emptyMap()
)

class FakeAnalyticsHelper : AnalyticsHelper {
    val loggedEvents = mutableListOf<LoggedAnalyticsEvent>()

    override fun logEvent(key: AnalyticsKey) {
        loggedEvents.add(LoggedAnalyticsEvent(key))
    }

    override fun logEvent(key: AnalyticsKey, paramBlock: AnalyticsParamsBuilder.() -> Unit) {
        val builder = AnalyticsParamsBuilder()
        builder.paramBlock()
        loggedEvents.add(LoggedAnalyticsEvent(key, builder.params.toMap()))
    }

    fun clear() {
        loggedEvents.clear()
    }
}
