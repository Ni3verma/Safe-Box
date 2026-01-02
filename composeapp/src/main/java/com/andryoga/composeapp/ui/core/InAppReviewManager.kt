package com.andryoga.composeapp.ui.core

import android.app.Activity
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManager
import timber.log.Timber
import javax.inject.Inject

class InAppReviewManager @Inject constructor(
    private val manager: ReviewManager
) {
    suspend fun requestAndLaunchReview(activity: Activity, inAppReviewSource: InAppReviewSource) {
        try {
            val reviewInfo = manager.requestReview()
            manager.launchReviewFlow(activity, reviewInfo)
        } catch (e: Exception) {
            Timber.e(e, "source: %s", inAppReviewSource)
        }
    }
}

enum class InAppReviewSource {
    SUCCESSFUL_RESTORE
}