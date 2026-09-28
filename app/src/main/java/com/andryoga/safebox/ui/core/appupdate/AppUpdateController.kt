package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow

/**
 * Boundary between the app and Google Play's in-app update API.
 *
 * Keeps Play types out of the ViewModel so the update policy can be unit tested with a plain fake.
 * Implementations must never throw: failures are reported as [AppUpdateState.NotAvailable] or a
 * `false` return, because an update check must never break the vault.
 *
 * Only the FLEXIBLE flow is offered. See ADR 0005 for why IMMEDIATE is out of scope.
 */
interface AppUpdateController {
    /**
     * Cold flow of update states. Each collection runs a fresh Play check and then follows the
     * install progress of an accepted update until it reaches a terminal state.
     */
    val state: Flow<AppUpdateState>

    /**
     * Launches Play's flexible update consent sheet for the most recent
     * [AppUpdateState.Available] emission.
     *
     * @param launcher Launcher registered with `StartIntentSenderForResult`. Its result code is the
     * user's answer to the consent sheet.
     * @return `true` if the consent sheet was launched, `false` if there was nothing to launch or
     * Play refused.
     */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean

    /**
     * Installs a downloaded update. Play restarts the app into the new version, so in the success
     * case this call does not return to a live process.
     */
    suspend fun completeUpdate()
}
