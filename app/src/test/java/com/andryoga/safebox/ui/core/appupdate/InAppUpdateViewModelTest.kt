package com.andryoga.safebox.ui.core.appupdate

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.andryoga.safebox.MainDispatcherRule
import com.andryoga.safebox.common.AnalyticsKey
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.test.fakes.FakeAnalyticsHelper
import com.andryoga.safebox.test.fakes.FakePreferenceProvider
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.google.android.play.core.install.model.ActivityResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Covers the in-app update policy: when the consent sheet is requested, the once-per-version and
 * once-per-process bounds, restart and silent install behaviour, the away-timeout lock, and every
 * analytics key the feature logs.
 *
 * A fresh [InAppUpdateSessionGuard] stands for a new process. Reusing the guard with a new
 * ViewModel stands for the activity being reopened within the same process.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InAppUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appUpdateController = FakeAppUpdateController()
    private var sessionGuard = InAppUpdateSessionGuard()
    private val logoutEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val activeSessionManager: ActiveSessionManager = mockk {
        every { logoutEvent } returns logoutEvents
    }
    private val preferenceProvider = FakePreferenceProvider()
    private val analyticsHelper = FakeAnalyticsHelper()
    private val launcher: ActivityResultLauncher<IntentSenderRequest> = mockk()

    @Test
    fun availableAndUnlocked_shouldExposePromptVersionCode() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))

        val viewModel = subscribedViewModel()

        assertThat(viewModel.uiState.value.promptVersionCode).isEqualTo(42)
    }

    @Test
    fun availableAndLocked_shouldNotPrompt() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))

        val viewModel = subscribedViewModel(isInHomeGraph = false)

        assertThat(viewModel.uiState.value.promptVersionCode).isNull()
    }

    @Test
    fun beforeHomeGraphReported_shouldNotPrompt() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = createViewModel()

        subscribe(viewModel)

        assertThat(viewModel.uiState.value).isEqualTo(InAppUpdateUiState())
    }

    @Test
    fun launchUpdateFlow_shouldStartFlexibleUpdateOnce() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        assertThat(appUpdateController.startFlexibleUpdateCount).isEqualTo(1)
    }

    @Test
    fun launchUpdateFlow_shouldStorePromptedVersionCode() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        assertThat(storedPromptedVersionCode()).isEqualTo(42)
    }

    @Test
    fun launchUpdateFlow_shouldLogFlowShow() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_FLOW_SHOW)).isEqualTo(1)
    }

    @Test
    fun launchUpdateFlow_shouldClearPromptVersionCode() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        assertThat(viewModel.uiState.value.promptVersionCode).isNull()
    }

    @Test
    fun launchUpdateFlow_shouldNotPauseSessionTimer() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        verify(exactly = 0) { activeSessionManager.setPaused(any()) }
    }

    @Test
    fun launchRefusedByPlay_shouldNotStoreVersionOrLogShow() = runTest {
        appUpdateController.startFlexibleUpdateResult = false
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        assertThat(storedPromptedVersionCode()).isEqualTo(NOT_STORED)
        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_FLOW_SHOW)).isFalse()
        assertThat(viewModel.uiState.value.promptVersionCode).isNull()
    }

    @Test
    fun promptedSameVersion_shouldNotRepromptWhateverResult() = runTest {
        listOf(
            Activity.RESULT_OK,
            Activity.RESULT_CANCELED,
            ActivityResult.RESULT_IN_APP_UPDATE_FAILED,
        ).forEach { resultCode ->
            preferenceProvider.clearAll()
            sessionGuard = InAppUpdateSessionGuard()
            appUpdateController.updateStates.emit(AppUpdateState.Available(42))
            val firstProcess = subscribedViewModel()
            firstProcess.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
            firstProcess.onAction(InAppUpdateAction.OnUpdateFlowResult(resultCode))
            runCurrent()

            sessionGuard = InAppUpdateSessionGuard()
            val nextProcess = subscribedViewModel()

            assertWithMessage("result code $resultCode")
                .that(nextProcess.uiState.value.promptVersionCode)
                .isNull()
        }
    }

    @Test
    fun newerVersionAfterPrompt_shouldReprompt() = runTest {
        preferenceProvider.upsertIntPref(CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE, 42)
        appUpdateController.updateStates.emit(AppUpdateState.Available(43))

        val viewModel = subscribedViewModel()

        assertThat(viewModel.uiState.value.promptVersionCode).isEqualTo(43)
    }

    @Test
    fun repeatedUnlock_shouldNotRelaunchFlowInSameProcess() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel()
        viewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(false))
        runCurrent()
        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(true))
        runCurrent()

        assertThat(viewModel.uiState.value.promptVersionCode).isNull()
    }

    @Test
    fun newViewModelInSameProcess_shouldNotRelaunchFlow() = runTest {
        // A refused launch stores nothing, so only the process-wide guard can block the retry.
        appUpdateController.startFlexibleUpdateResult = false
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val firstViewModel = subscribedViewModel()
        firstViewModel.onAction(InAppUpdateAction.OnLaunchUpdateFlow(42, launcher))
        runCurrent()

        val reopenedViewModel = subscribedViewModel()

        assertThat(reopenedViewModel.uiState.value.promptVersionCode).isNull()
    }

    @Test
    fun flowResultOk_shouldLogFlowAccept() {
        createViewModel().onAction(InAppUpdateAction.OnUpdateFlowResult(Activity.RESULT_OK))

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_FLOW_ACCEPT)).isTrue()
    }

    @Test
    fun flowResultCanceled_shouldLogFlowCancel() {
        createViewModel().onAction(InAppUpdateAction.OnUpdateFlowResult(Activity.RESULT_CANCELED))

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_FLOW_CANCEL)).isTrue()
    }

    @Test
    fun flowResultInAppUpdateFailed_shouldLogFlowFailed() {
        createViewModel().onAction(
            InAppUpdateAction.OnUpdateFlowResult(ActivityResult.RESULT_IN_APP_UPDATE_FAILED),
        )

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_FLOW_FAILED)).isTrue()
    }

    @Test
    fun flowResultUnknownCode_shouldLogFlowFailed() {
        createViewModel().onAction(InAppUpdateAction.OnUpdateFlowResult(UNKNOWN_RESULT_CODE))

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_FLOW_FAILED)).isTrue()
    }

    @Test
    fun downloadedAsFirstEmission_shouldNotLogDownloaded() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)

        subscribedViewModel()

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_DOWNLOADED)).isFalse()
    }

    @Test
    fun downloadingToDownloaded_shouldLogDownloadedOnce() = runTest {
        subscribedViewModel()

        repeat(2) {
            emitUpdateState(AppUpdateState.Downloading)
            emitUpdateState(AppUpdateState.Downloaded)
        }

        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_DOWNLOADED)).isEqualTo(1)
    }

    @Test
    fun downloadFinishedWhileNotObserved_shouldLogDownloadedOnNextCheck() = runTest {
        val viewModel = createViewModel()
        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(true))
        val subscription = subscribe(viewModel)
        emitUpdateState(AppUpdateState.Downloading)
        subscription.cancel()
        advanceTimeBy(STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)

        subscribe(viewModel)

        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_DOWNLOADED)).isEqualTo(1)
    }

    @Test
    fun downloadedAndUnlocked_shouldShowRestartPromptAndLogShowOnce() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)

        val viewModel = subscribedViewModel()

        assertThat(viewModel.uiState.value.showRestartPrompt).isTrue()
        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_SHOW))
            .isEqualTo(1)
    }

    @Test
    fun stateReEmission_shouldNotRelogRestartSnackbarShow() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()

        emitUpdateState(AppUpdateState.Downloaded)
        val reopenedViewModel = subscribedViewModel()

        assertThat(viewModel.uiState.value.showRestartPrompt).isTrue()
        assertThat(reopenedViewModel.uiState.value.showRestartPrompt).isTrue()
        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_SHOW))
            .isEqualTo(1)
    }

    @Test
    fun downloadedAndLocked_shouldCompleteUpdateSilentlyOnce() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel(isInHomeGraph = false)

        emitUpdateState(AppUpdateState.Downloaded)

        assertThat(viewModel.uiState.value.showRestartPrompt).isFalse()
        assertThat(appUpdateController.completeUpdateCount).isEqualTo(1)
    }

    @Test
    fun downloadedAndLocked_shouldLogAutoCompleteOnce() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        subscribedViewModel(isInHomeGraph = false)

        emitUpdateState(AppUpdateState.Downloaded)

        assertThat(analyticsHelper.count(AnalyticsKey.IN_APP_UPDATE_AUTO_COMPLETE)).isEqualTo(1)
    }

    @Test
    fun restartClick_shouldCompleteUpdate() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnRestartClick)
        runCurrent()

        assertThat(appUpdateController.completeUpdateCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.showRestartPrompt).isFalse()
    }

    @Test
    fun restartClick_shouldLogRestartSnackbarClick() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnRestartClick)
        runCurrent()

        assertThat(analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_CLICK))
            .isTrue()
    }

    @Test
    fun restartPromptDismissed_shouldHidePromptWithoutCompleting() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnRestartPromptDismissed)
        runCurrent()

        assertThat(viewModel.uiState.value.showRestartPrompt).isFalse()
        assertThat(appUpdateController.completeUpdateCount).isEqualTo(0)
    }

    @Test
    fun restartPromptDismissed_shouldLogRestartSnackbarDismissed() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()

        viewModel.onAction(InAppUpdateAction.OnRestartPromptDismissed)
        runCurrent()

        assertThat(
            analyticsHelper.hasLogged(AnalyticsKey.IN_APP_UPDATE_RESTART_SNACKBAR_DISMISSED),
        ).isTrue()
    }

    @Test
    fun restartPromptDismissedThenLock_shouldAutoComplete() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()
        viewModel.onAction(InAppUpdateAction.OnRestartPromptDismissed)
        runCurrent()

        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(false))
        runCurrent()

        assertThat(appUpdateController.completeUpdateCount).isEqualTo(1)
    }

    @Test
    fun awayTimeoutInHomeGraph_shouldCompleteDownloadedUpdateSilently() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Downloaded)
        val viewModel = subscribedViewModel()
        assertThat(viewModel.uiState.value.showRestartPrompt).isTrue()

        logoutEvents.emit(Unit)
        runCurrent()

        assertThat(viewModel.uiState.value.showRestartPrompt).isFalse()
        assertThat(appUpdateController.completeUpdateCount).isEqualTo(1)
    }

    @Test
    fun awayTimeoutOnLoginScreen_shouldNotBlockNextUnlock() = runTest {
        appUpdateController.updateStates.emit(AppUpdateState.Available(42))
        val viewModel = subscribedViewModel(isInHomeGraph = false)
        logoutEvents.emit(Unit)
        runCurrent()

        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(true))
        runCurrent()

        assertThat(viewModel.uiState.value.promptVersionCode).isEqualTo(42)
    }

    private fun createViewModel() = InAppUpdateViewModel(
        appUpdateController = appUpdateController,
        sessionGuard = sessionGuard,
        activeSessionManager = activeSessionManager,
        preferenceProvider = preferenceProvider,
        analyticsHelper = analyticsHelper,
    )

    /**
     * Creates a ViewModel, reports the root destination and keeps its state subscribed, as the
     * app-level host does.
     */
    private fun TestScope.subscribedViewModel(isInHomeGraph: Boolean = true): InAppUpdateViewModel {
        val viewModel = createViewModel()
        viewModel.onAction(InAppUpdateAction.OnHomeGraphStateChanged(isInHomeGraph))
        subscribe(viewModel)
        return viewModel
    }

    private fun TestScope.subscribe(viewModel: InAppUpdateViewModel): Job {
        val subscription = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        return subscription
    }

    private suspend fun TestScope.emitUpdateState(state: AppUpdateState) {
        appUpdateController.updateStates.emit(state)
        runCurrent()
    }

    private suspend fun storedPromptedVersionCode(): Int = preferenceProvider.getIntPref(
        CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE,
        NOT_STORED,
    )

    /**
     * Controller whose state stream replays its latest value to each new collector, the way each
     * collection of the real controller runs a fresh Play check.
     */
    private class FakeAppUpdateController : AppUpdateController {
        val updateStates = MutableSharedFlow<AppUpdateState>(replay = 1)
        var startFlexibleUpdateResult = true
        var startFlexibleUpdateCount = 0
        var completeUpdateCount = 0

        override val state: Flow<AppUpdateState> = updateStates

        override fun startFlexibleUpdate(
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ): Boolean {
            startFlexibleUpdateCount++
            return startFlexibleUpdateResult
        }

        override suspend fun completeUpdate() {
            completeUpdateCount++
        }
    }

    private companion object {
        const val NOT_STORED = -1
        const val UNKNOWN_RESULT_CODE = 99
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
