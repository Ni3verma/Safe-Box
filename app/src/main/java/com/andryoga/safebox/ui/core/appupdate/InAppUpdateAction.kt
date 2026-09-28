package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Actions dispatched from [InAppUpdateHostRoot] and [InAppUpdateRestartPromptRoot] to
 * [InAppUpdateViewModel].
 */
sealed interface InAppUpdateAction {
    /** The root navigation moved into or out of the Home graph. */
    class OnHomeGraphStateChanged(val isInHomeGraph: Boolean) : InAppUpdateAction

    /**
     * The host is resumed and ready to show Play's consent sheet for [versionCode]. Play's API
     * needs the [launcher] to start its sheet, and the ViewModel needs to know whether Play
     * accepted the launch, so it is handed straight to the controller and is not retained.
     */
    class OnLaunchUpdateFlow(
        val versionCode: Int,
        val launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) : InAppUpdateAction

    /** Play's consent sheet closed with [result]. */
    class OnUpdateFlowResult(val result: UpdateFlowResult) : InAppUpdateAction

    /** The user tapped Restart on the snackbar. */
    object OnRestartClick : InAppUpdateAction

    /**
     * The snackbar ended without its action: the user tapped ✕, or another caller dismissed it.
     */
    object OnRestartPromptDismissed : InAppUpdateAction
}
