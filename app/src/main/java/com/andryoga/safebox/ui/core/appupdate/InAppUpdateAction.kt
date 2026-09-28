package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Actions dispatched from [InAppUpdateHost] and [InAppUpdateRestartPrompt] to
 * [InAppUpdateViewModel].
 */
sealed interface InAppUpdateAction {
    /** The root navigation moved into or out of the Home graph. */
    class OnHomeGraphStateChanged(val isInHomeGraph: Boolean) : InAppUpdateAction

    /**
     * The host is resumed and ready to show Play's consent sheet for [versionCode]. The
     * [launcher] is handed straight to the controller and is not retained.
     */
    class OnLaunchUpdateFlow(
        val versionCode: Int,
        val launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) : InAppUpdateAction

    /** Play's consent sheet returned [resultCode]. */
    class OnUpdateFlowResult(val resultCode: Int) : InAppUpdateAction

    /** The user tapped Restart on the snackbar. */
    object OnRestartClick : InAppUpdateAction

    /**
     * The snackbar ended without its action: the user tapped ✕, or another caller dismissed it.
     */
    object OnRestartPromptDismissed : InAppUpdateAction
}
