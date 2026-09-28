package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.ktx.AppUpdateResult
import com.google.android.play.core.ktx.requestCompleteUpdate
import com.google.android.play.core.ktx.requestUpdateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppUpdateController] backed by Google Play's in-app update API.
 *
 * Wraps the ktx `requestUpdateFlow()`, which registers and unregisters the install state listener
 * for the lifetime of each collection. Every Play failure is swallowed: a failed check becomes
 * [AppUpdateState.NotAvailable], and the app does not log it, so a release build on a device
 * without Play produces no noise.
 *
 * @param appUpdateManager Play's manager. In androidTest this is `FakeAppUpdateManager`.
 */
@Singleton
class PlayAppUpdateController @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
) : AppUpdateController {

    // Play needs the AppUpdateInfo from the check to start a flow, and each info can start only
    // one flow, so it is cleared on use and on any emission that is not a fresh offer.
    @Volatile
    private var pendingUpdateInfo: AppUpdateInfo? = null

    override val state: Flow<AppUpdateState> = appUpdateManager.requestUpdateFlow()
        .map { result ->
            val state = AppUpdateStateMapper.map(result)
            pendingUpdateInfo = if (state is AppUpdateState.Available) {
                (result as AppUpdateResult.Available).updateInfo
            } else {
                null
            }
            state
        }
        .catch { emit(AppUpdateState.NotAvailable) }

    override fun startFlexibleUpdate(
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean {
        val updateInfo = pendingUpdateInfo ?: return false
        pendingUpdateInfo = null
        return runCatching {
            appUpdateManager.startUpdateFlowForResult(
                updateInfo,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
            )
        }.getOrDefault(false)
    }

    override suspend fun completeUpdate() {
        try {
            appUpdateManager.requestCompleteUpdate()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play could not install. The update stays downloaded, so the next cold start, which
            // begins locked, tries again. Nothing is shown because the user cannot act on it.
        }
    }
}
