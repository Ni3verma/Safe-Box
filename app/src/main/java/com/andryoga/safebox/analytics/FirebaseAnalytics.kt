package com.andryoga.safebox.analytics

import androidx.core.os.bundleOf
import com.andryoga.safebox.common.AnalyticsKey
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import timber.log.Timber

class FirebaseAnalytics : AnalyticsHelper {
    override fun logEvent(key: AnalyticsKey) {
        Timber.i("logEvent: $key")
        Firebase.analytics.logEvent(key.eventName, null)
    }

    override fun logEvent(
        key: AnalyticsKey,
        paramBlock: AnalyticsParamsBuilder.() -> Unit

    ) {
        val builder = AnalyticsParamsBuilder()
        paramBlock(builder)
        Timber.i("logEvent: $key, param keys: ${builder.params.keys}")
        Firebase.analytics.logEvent(
            key.eventName,
            bundleOf(*builder.params.toList().toTypedArray())
        )
    }
}