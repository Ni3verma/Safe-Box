package com.andryoga.safebox.ui.core

import android.content.ClipData
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Remembers a reusable "copy to clipboard" action wired to haptics and the global snackbar.
 *
 * Centralises the Android 13 (Tiramisu) behaviour: from Tiramisu onwards the system shows its own
 * clipboard overlay, so the in-app confirmation snackbar is suppressed to avoid a double
 * confirmation. Every copy affordance in the app should go through this so the behaviour stays
 * consistent in one place.
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

    return remember(clipboard, haptic, snackBarHost, scope) {
        { label, value, confirmationMessage ->
            scope.launch {
                Timber.i("setting clip entry for $label")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))

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
