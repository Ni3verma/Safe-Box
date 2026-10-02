package com.andryoga.safebox.ui.core.appupdate.controller

/**
 * Play-agnostic view of the in-app update lifecycle, as seen by the rest of the app.
 *
 * Only the states the app acts on are modelled. Every Play outcome that needs no action, such as a
 * failed check, a cancelled download or a device without Play, collapses into [NotAvailable], so
 * callers never handle Play error codes. See `docs/architecture/in-app-updates.md`.
 */
sealed interface AppUpdateState {
    /** No actionable update: none on Play, the check failed, or a previous attempt ended. */
    data object NotAvailable : AppUpdateState

    /**
     * A flexible update can be started.
     *
     * @property versionCode Version code Play is offering. It is persisted when the consent sheet
     * is launched, so the user is asked at most once per Play version.
     */
    data class Available(val versionCode: Int) : AppUpdateState

    /** Play is working on an accepted update: pending, downloading or installing. */
    data object Downloading : AppUpdateState

    /** The update is downloaded and only needs a restart to install. */
    data object Downloaded : AppUpdateState
}
