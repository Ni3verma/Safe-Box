package com.andryoga.safebox.ui.core.appupdate

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.AppUpdateResult

/**
 * Pure translation from Play's [AppUpdateResult] to the app's [AppUpdateState].
 *
 * It is kept separate from [PlayAppUpdateController] so every Play status can be unit tested
 * without a device.
 */
object AppUpdateStateMapper {

    // Install statuses meaning Play is still working on an accepted update.
    private val IN_PROGRESS_INSTALL_STATUSES = setOf(
        InstallStatus.PENDING,
        InstallStatus.DOWNLOADING,
        InstallStatus.INSTALLING,
    )

    /**
     * @param result Latest emission of `AppUpdateManager.requestUpdateFlow()`.
     * @return The state the app should act on.
     */
    fun map(result: AppUpdateResult): AppUpdateState = when (result) {
        AppUpdateResult.NotAvailable -> AppUpdateState.NotAvailable
        is AppUpdateResult.Available -> mapAvailable(result.updateInfo)
        is AppUpdateResult.InProgress -> mapInstallStatus(result.installState.installStatus())
        is AppUpdateResult.Downloaded -> AppUpdateState.Downloaded
    }

    /**
     * Play's `requestUpdateFlow()` also reports [AppUpdateResult.Available] when a check lands
     * while an accepted update is still downloading, for example on a cold start mid-download.
     * So the install status is checked before the result is treated as a fresh offer.
     *
     * An offer only counts if the FLEXIBLE type is allowed, because that is the only flow the
     * app starts.
     */
    private fun mapAvailable(info: AppUpdateInfo): AppUpdateState {
        val installState = mapInstallStatus(info.installStatus())
        if (installState != AppUpdateState.NotAvailable) return installState
        val isFlexibleOffer = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        return if (isFlexibleOffer) {
            AppUpdateState.Available(info.availableVersionCode())
        } else {
            AppUpdateState.NotAvailable
        }
    }

    /**
     * Terminal failures, meaning `FAILED`, `CANCELED` and `UNKNOWN`, map to
     * [AppUpdateState.NotAvailable]. The user is only asked once per version anyway, and an
     * [AppUpdateInfo] can start only one flow, so there is nothing left to act on.
     */
    private fun mapInstallStatus(installStatus: Int): AppUpdateState = when (installStatus) {
        in IN_PROGRESS_INSTALL_STATUSES -> AppUpdateState.Downloading
        InstallStatus.DOWNLOADED -> AppUpdateState.Downloaded
        else -> AppUpdateState.NotAvailable
    }
}
