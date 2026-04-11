package com.andryoga.safebox.analytics

import com.andryoga.safebox.common.AnalyticsKey

interface AnalyticsHelper {
    fun logEvent(key: AnalyticsKey)
    fun logEvent(key: AnalyticsKey, paramBlock: AnalyticsParamsBuilder.() -> Unit)
}