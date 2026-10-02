package com.andryoga.safebox.ui.core.appupdate.controller

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult

// Install statuses meaning Play is still working on an accepted update.
private val IN_PROGRESS_INSTALL_STATUSES = setOf(
    InstallStatus.PENDING,
    InstallStatus.DOWNLOADING,
    InstallStatus.INSTALLING,
)

/**
 * Pure translation from Play's [AppUpdateResult] to the app's [AppUpdateState].
 *
 * It is a top-level function rather than a private member of [PlayAppUpdateController] because
 * the controller cannot run on the JVM: the ktx `requestUpdateFlow()` delivers every result on the
 * main looper, which unit tests do not have. Keeping the translation separate is what lets every
 * Play status be unit tested without a device.
 *
 * @receiver Latest emission of `AppUpdateManager.requestUpdateFlow()`.
 * @return The state the app should act on.
 */
internal fun AppUpdateResult.toAppUpdateState(): AppUpdateState = when (this) {
    AppUpdateResult.NotAvailable -> AppUpdateState.NotAvailable
    is AppUpdateResult.Available -> updateInfo.toAppUpdateState()
    is AppUpdateResult.InProgress -> acceptedUpdateState(installState.installStatus())
        ?: AppUpdateState.NotAvailable
    is AppUpdateResult.Downloaded -> AppUpdateState.Downloaded
}

/**
 * Play's `requestUpdateFlow()` also reports [AppUpdateResult.Available] when a check lands while
 * an accepted update is still downloading, for example on a cold start mid-download. So an update
 * the user already accepted wins over treating the result as a fresh offer.
 */
private fun AppUpdateInfo.toAppUpdateState(): AppUpdateState {
    val acceptedUpdate = acceptedUpdateState(installStatus())
    return when {
        acceptedUpdate != null -> acceptedUpdate
        isFlexibleOffer() -> AppUpdateState.Available(availableVersionCode())
        else -> AppUpdateState.NotAvailable
    }
}

/**
 * An offer only counts if the FLEXIBLE type is allowed. That is true only when Play attached the
 * flexible flow's `PendingIntent`, which is what the consent sheet is launched from. Play leaves it
 * out when the type is not allowed; `getFailedUpdatePreconditions` lists reasons such as
 * `INSUFFICIENT_STORAGE`. A launch then cannot succeed and would only use up the one attempt
 * allowed per process.
 */
private fun AppUpdateInfo.isFlexibleOffer(): Boolean =
    updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
        isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

/**
 * @param installStatus One of Play's `InstallStatus` constants.
 * @return [AppUpdateState.Downloading] while Play works on an accepted update,
 * [AppUpdateState.Downloaded] once it only needs a restart, or `null` when there is no accepted
 * update to follow. That includes the terminal statuses `FAILED`, `CANCELED` and `UNKNOWN`: the
 * user is only asked once per version anyway, and an [AppUpdateInfo] can start only one flow, so
 * there is nothing left to act on.
 */
private fun acceptedUpdateState(installStatus: Int): AppUpdateState? = when (installStatus) {
    in IN_PROGRESS_INSTALL_STATUSES -> AppUpdateState.Downloading
    InstallStatus.DOWNLOADED -> AppUpdateState.Downloaded
    else -> null
}
