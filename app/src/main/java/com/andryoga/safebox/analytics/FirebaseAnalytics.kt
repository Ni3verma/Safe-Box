package com.andryoga.safebox.analytics

import android.os.Bundle
import com.andryoga.safebox.common.AnalyticsKey
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import timber.log.Timber

class FirebaseAnalytics : AnalyticsHelper {
    override fun logEvent(key: AnalyticsKey) {
        logEvent(key = key, params = null)
    }

    override fun logEvent(
        key: AnalyticsKey,
        params: Bundle?
    ) {
        Timber.i("logEvent: $key, $params")
        Firebase.analytics.logEvent(key.eventName, params)
    }
}