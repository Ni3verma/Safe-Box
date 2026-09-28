package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * [AppUpdateController] used when `BuildConfig.IN_APP_UPDATE_ENABLED` is off, that is in `debug`
 * and `qa`.
 *
 * Play can only offer updates to the application id it distributes, so for the `.debug` and `.qa`
 * ids every check would fail anyway. Turning the feature off also keeps `:upgrade-test` runs free
 * of Play calls.
 */
class NoOpAppUpdateController : AppUpdateController {
    override val state: Flow<AppUpdateState> = flowOf(AppUpdateState.NotAvailable)

    override fun startFlexibleUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean = false

    override suspend fun completeUpdate() = Unit
}
