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
     * The host is resumed and can show Play's consent sheet. Fired only while
     * [InAppUpdateUiState.promptVersionCode] is set; the ViewModel decides from its own state which
     * version is offered, and whether an attempt is still allowed.
     *
     * Play's `startUpdateFlowForResult` has no overload that works without an Activity-bound
     * launcher, so the UI hands [launcher] over at launch time. The ViewModel passes it straight
     * to the controller and does not retain it.
     */
    class OnReadyToLaunchUpdateFlow(
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
