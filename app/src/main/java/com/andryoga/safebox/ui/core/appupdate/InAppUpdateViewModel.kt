package com.andryoga.safebox.ui.core.appupdate

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andryoga.safebox.analytics.AnalyticsHelper
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.di.ApplicationScope
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.appupdate.controller.AppUpdateController
import com.andryoga.safebox.ui.core.appupdate.controller.AppUpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Stored prompted version code before the user was ever asked. Play version codes are positive.
private const val NO_PROMPTED_VERSION_CODE = 0

/**
 * Activity-scoped ViewModel that owns the in-app update policy. The policy:
 *
 * - **Prompt only while unlocked.** The login screen launches `BiometricPrompt` by itself, and
 *   Play's consent sheet would compete with it.
 * - **Ask once per Play version.** The offered version code is persisted when the consent sheet
 *   is launched, whatever the user answers, and the user is only asked again for a higher version.
 * - **Restart only by choice when unlocked, silently when locked.** A restart discards unsaved
 *   edits and forces a new login, so while unlocked it waits for the snackbar's Restart action.
 *   While locked there is nothing to lose, so the downloaded update is installed immediately.
 *
 * The vault counts as locked as soon as the away timeout fires, even though the away dialog still
 * sits inside the Home graph. The only way past that dialog is a new login, so a pending install
 * can run in the background right away instead of waiting for the user to come back.
 *
 * Analytics are bounded as described in `docs/architecture/in-app-updates.md`. The controller
 * re-emits on every check, so no event is logged per emission.
 *
 * @param appUpdateController Play boundary. It is a no-op outside `release`.
 * @param sessionGuard Per-process flags that outlive this ViewModel.
 * @param activeSessionManager Source of the away-timeout event that locks the vault.
 * @param preferenceProvider Stores the last prompted version code across processes.
 * @param analyticsHelper Analytics logger.
 * @param applicationScope Process-wide scope for the prompted-version write, which must outlive
 * this ViewModel: finishing the activity while Play's sheet is open would otherwise cancel the
 * only once-per-version marker before it reaches disk.
 */
@HiltViewModel
class InAppUpdateViewModel @Inject constructor(
    private val appUpdateController: AppUpdateController,
    private val sessionGuard: InAppUpdateSessionGuard,
    activeSessionManager: ActiveSessionManager,
    private val preferenceProvider: PreferenceProvider,
    private val analyticsHelper: AnalyticsHelper,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    // Driving state reported by the root nav host. Null until the first report, so no decision is
    // made before the real destination is known.
    private val isInHomeGraph = MutableStateFlow<Boolean?>(null)

    // Entering the Home graph unlocks. Leaving it, or the away timeout firing inside it, locks.
    // StateFlow only emits on change, so a lock caused by the timeout holds until the next login.
    private val isVaultUnlocked: Flow<Boolean> = merge(
        isInHomeGraph.filterNotNull(),
        activeSessionManager.logoutEvent.map { false },
    ).distinctUntilChanged()

    private val promptedVersionCode: Flow<Int> = flow {
        emit(
            preferenceProvider.getIntPref(
                CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE,
                NO_PROMPTED_VERSION_CODE,
            ),
        )
    }

    val uiState: StateFlow<InAppUpdateUiState> = combine(
        appUpdateController.state.onEach(::trackDownloadCompletion),
        isVaultUnlocked,
        promptedVersionCode,
        sessionGuard.isFlowLaunched,
        sessionGuard.isRestartPromptResolved,
    ) { updateState, isUnlocked, promptedVersion, isFlowLaunched, isRestartPromptResolved ->
        val isDownloaded = updateState == AppUpdateState.Downloaded
        if (isDownloaded && !isUnlocked) completeUpdateSilently()
        InAppUpdateUiState(
            promptVersionCode = (updateState as? AppUpdateState.Available)?.versionCode
                ?.takeIf { isUnlocked && !isFlowLaunched && it > promptedVersion },
            showRestartPrompt = isDownloaded && isUnlocked && !isRestartPromptResolved,
        )
    }
        .onEach { if (it.showRestartPrompt) logRestartPromptShown() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InAppUpdateUiState(),
        )

    fun onAction(action: InAppUpdateAction) {
        when (action) {
            is InAppUpdateAction.OnHomeGraphStateChanged -> {
                isInHomeGraph.value = action.isInHomeGraph
            }

            is InAppUpdateAction.OnReadyToLaunchUpdateFlow -> {
                launchUpdateFlow(action.launcher)
            }

            is InAppUpdateAction.OnUpdateFlowResult -> {
                analyticsHelper.logEvent(toFlowResultKey(action.result))
            }

            InAppUpdateAction.OnRestartClick -> {
                sessionGuard.isRestartPromptResolved.value = true
                analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_CLICK)
                completeUpdate()
            }

            InAppUpdateAction.OnRestartPromptDismissed -> {
                sessionGuard.isRestartPromptResolved.value = true
                analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_DISMISSED)
            }
        }
    }

    /**
     * Attempts the consent sheet at most once per process. The version is only persisted when
     * Play actually launched the sheet. A refused launch is logged and retried on the next cold
     * start instead of silently using up this version's only prompt.
     */
    private fun launchUpdateFlow(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        if (sessionGuard.isFlowLaunched.value) return
        // Read before the guard flips: the flip recomputes uiState with promptVersionCode = null.
        val versionCode = uiState.value.promptVersionCode ?: return
        sessionGuard.isFlowLaunched.value = true
        if (!appUpdateController.startFlexibleUpdate(launcher)) {
            analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_FLOW_LAUNCH_FAILED)
            return
        }
        analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_FLOW_SHOW)
        // Not viewModelScope: the sheet is a Play Store activity, so the user can finish this
        // activity while it is open, and the write must still land to keep "once per version".
        applicationScope.launch {
            preferenceProvider.upsertIntPref(
                CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE,
                versionCode,
            )
        }
    }

    /**
     * Logs `IN_APP_UPDATE_DOWNLOADED` only on an observed `Downloading → Downloaded` transition.
     * An update that was already downloaded at the first check was counted by the process that saw
     * it finish.
     */
    private fun trackDownloadCompletion(state: AppUpdateState) {
        val previous = sessionGuard.lastObservedState
        sessionGuard.lastObservedState = state
        val isDownloadCompletion = previous == AppUpdateState.Downloading &&
            state == AppUpdateState.Downloaded
        if (!isDownloadCompletion || sessionGuard.isDownloadedLogged) return
        sessionGuard.isDownloadedLogged = true
        analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_DOWNLOADED)
    }

    private fun logRestartPromptShown() {
        if (sessionGuard.isRestartPromptShownLogged) return
        sessionGuard.isRestartPromptShownLogged = true
        analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_SHOW)
    }

    /**
     * Installs a downloaded update while the vault is locked, at most once per process. A
     * successful install restarts the process, so a repeat across cold starts means Play keeps
     * failing, which is exactly the signal worth seeing.
     */
    private fun completeUpdateSilently() {
        if (sessionGuard.isAutoCompleteRequested) return
        sessionGuard.isAutoCompleteRequested = true
        analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_AUTO_COMPLETE)
        completeUpdate()
    }

    /**
     * Asks Play to install the downloaded update. On success the process restarts, so only the
     * failure is observable and logged.
     */
    private fun completeUpdate() {
        viewModelScope.launch {
            if (!appUpdateController.completeUpdate()) {
                analyticsHelper.logEvent(AnalyticsKey.IN_APP_UPDATE_COMPLETE_FAILED)
            }
        }
    }

    /**
     * @param result Outcome of Play's consent sheet.
     * @return The analytics key for that outcome.
     */
    private fun toFlowResultKey(result: UpdateFlowResult): AnalyticsKey = when (result) {
        UpdateFlowResult.ACCEPTED -> AnalyticsKey.IN_APP_UPDATE_FLOW_ACCEPT
        UpdateFlowResult.CANCELED -> AnalyticsKey.IN_APP_UPDATE_FLOW_CANCEL
        UpdateFlowResult.FAILED -> AnalyticsKey.IN_APP_UPDATE_FLOW_FAILED
    }
}
