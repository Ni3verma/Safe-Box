package com.andryoga.safebox.ui.core

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.worker.ClipboardClearWorker
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Remembers a reusable "copy to clipboard" action wired to haptics and the global snackbar.
 *
 * Centralises the Android 13 (Tiramisu) behaviour: from Tiramisu onwards the system shows its own
 * clipboard overlay, so the in-app confirmation snackbar is suppressed to avoid a double
 * confirmation. Every copy affordance in the app should go through this so the behaviour stays
 * consistent in one place.
 *
 * Every copy is also queued for automatic clearing, so a credential does not sit on the system
 * clipboard indefinitely. See [ClipboardClearWorker].
 *
 * @return lambda accepting the clipboard entry label, the raw value to copy, and the message shown
 * on pre Tiramisu devices.
 */
@Composable
fun rememberCopyToClipboardAction(): (label: String, value: String, confirmationMessage: String) -> Unit {
    val clipboard = LocalClipboard.current
    val haptic = LocalHapticFeedback.current
    val snackBarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    return remember(clipboard, haptic, snackBarHost, scope, context) {
        { label, value, confirmationMessage ->
            scope.launch {
                Timber.i("setting clip entry for $label")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val clipId = UUID.randomUUID().toString()
                // flag the clip as sensitive so the Tiramisu+ clipboard preview overlay redacts
                // it instead of showing the credential in clear text.
                val clipData = ClipData.newPlainText(label, value).apply {
                    description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        putString(CommonConstants.CLIPBOARD_CLIP_ID, clipId)
                    }
                }
                clipboard.setClipEntry(ClipEntry(clipData))
                scheduleClipboardClear(context, clipId)

                // Android 13 (Tiramisu) introduced the system-level clipboard overlay.
                // so need to show our own snackbar only below it.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackBarHost.currentSnackbarData?.dismiss()
                    snackBarHost.showSnackbar(
                        message = confirmationMessage,
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }
}

/**
 * Queues the clear for the clip that was just written.
 *
 * Enqueued as unique work so a fresh copy replaces the clear still pending for the previous one,
 * which both measures the delay from the latest copy and keeps a single row in the WorkManager
 * database no matter how often the user copies.
 *
 * @param context Any context, used only to reach the WorkManager singleton.
 * @param clipId Id embedded in the clip, so the worker can tell it apart from a later clip written
 * by another app.
 */
private fun scheduleClipboardClear(context: Context, clipId: String) {
    val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
        .setInitialDelay(CommonConstants.CLIPBOARD_CLEAR_DELAY_SECONDS, TimeUnit.SECONDS)
        .setInputData(workDataOf(CommonConstants.CLIPBOARD_CLIP_ID to clipId))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        CommonConstants.WORKER_NAME_CLEAR_CLIPBOARD,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}
