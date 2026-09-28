package com.andryoga.safebox.ui.core.appupdate

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide memory of what the in-app update flow has already done.
 *
 * This is a singleton rather than fields on [InAppUpdateViewModel] because the activity-scoped
 * ViewModel is recreated whenever the user backs out of the app and reopens it within the same
 * process. Flags held by the ViewModel would reset at that point and allow duplicate prompts and
 * analytics events. Everything here resets only when the process dies. Anything that must survive
 * process death, which is only the prompted version code, lives in preferences instead.
 *
 * Only accessed from the main thread.
 */
@Singleton
class InAppUpdateSessionGuard @Inject constructor() {
    /** The consent sheet was attempted in this process. Feeds derived state, so it is a flow. */
    val isFlowLaunched = MutableStateFlow(false)

    /** The restart snackbar was answered in this process. Feeds derived state, so it is a flow. */
    val isRestartPromptResolved = MutableStateFlow(false)

    /** `IN_APP_UPDATE_DOWNLOADED` was logged in this process. */
    var isDownloadedLogged = false

    /** `IN_APP_UPDATE_RESTART_SNACKBAR_SHOW` was logged in this process. */
    var isRestartPromptShownLogged = false

    /** A silent `completeUpdate()` was requested in this process. */
    var isAutoCompleteRequested = false

    /**
     * Last update state seen in this process, used to detect transitions. It outlives a single
     * collection, so a download that finishes while the app is not observing is still detected
     * on the next check.
     */
    var lastObservedState: AppUpdateState? = null
}
