package com.andryoga.safebox.analytics

import com.andryoga.safebox.common.AnalyticsParam

class AnalyticsParamsBuilder {
    internal val params = mutableMapOf<String, Any>()

    fun param(key: AnalyticsParam, value: String) {
        params[key.paramName] = value
    }

    fun param(key: AnalyticsParam, value: Boolean) {
        params[key.paramName] = value
    }

    fun param(key: AnalyticsParam, value: Int) {
        params[key.paramName] = value
    }

    fun param(key: AnalyticsParam, value: Long) {
        params[key.paramName] = value
    }

    fun param(key: AnalyticsParam, value: Double) {
        params[key.paramName] = value
    }
}