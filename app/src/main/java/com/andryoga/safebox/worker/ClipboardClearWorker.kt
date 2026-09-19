package com.andryoga.safebox.worker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Wipes a credential this app copied to the system clipboard once the auto clear delay has passed.
 *
 * Runs through WorkManager rather than a coroutine delay so the clear still happens after the app
 * process is killed, which is the common case: the user copies a code and immediately leaves for
 * another app.
 *
 * The clip this app wrote carries a random id in its [android.content.ClipDescription] extras and
 * the same id arrives here as worker input. When the two disagree the user has copied something
 * else in the meantime and their clip is left alone. From Android 10 a background app cannot read
 * the clipboard at all, so that check is best effort: an unreadable clipboard is cleared anyway,
 * because leaving a credential behind is the worse failure.
 */
@HiltWorker
class ClipboardClearWorker
@AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyticsHelper: AnalyticsHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val clipboardManager = applicationContext.getSystemService(ClipboardManager::class.java)
        if (clipboardManager == null) {
            Timber.w("clipboard service unavailable, nothing to clear")
            return Result.success()
        }

        val description = clipboardManager.primaryClipDescription
        val currentClipId = description?.extras?.getString(CommonConstants.CLIPBOARD_CLIP_ID)
        val expectedClipId = inputData.getString(CommonConstants.CLIPBOARD_CLIP_ID)
        if (description != null && currentClipId != expectedClipId) {
            Timber.i("clipboard holds a clip this app did not write, skipping clear")
            analyticsHelper.logEvent(AnalyticsKey.CLIPBOARD_AUTO_CLEAR_SKIPPED)
            return Result.success()
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                // clearPrimaryClip only arrived in Pie, so below it the secret can only be
                // displaced by overwriting the clipboard with an empty clip.
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            Timber.i("cleared the copied credential from the clipboard")
            analyticsHelper.logEvent(AnalyticsKey.CLIPBOARD_AUTO_CLEARED)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "failed to clear the clipboard")
            Result.failure()
        }
    }
}
