package com.andryoga.safebox.analytics

import android.os.Bundle
import com.andryoga.safebox.common.AnalyticsKey

interface AnalyticsHelper {
    fun logEvent(key: AnalyticsKey)
    fun logEvent(key: AnalyticsKey, params: Bundle?)
}