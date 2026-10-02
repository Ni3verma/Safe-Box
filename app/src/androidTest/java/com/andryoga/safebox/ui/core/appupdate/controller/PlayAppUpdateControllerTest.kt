package com.andryoga.safebox.ui.core.appupdate.controller

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.BuildConfig
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [PlayAppUpdateController] over Play's [FakeAppUpdateManager]. This is instrumentation
 * rather than a unit test because the ktx `requestUpdateFlow()` and `requestCompleteUpdate()`
 * deliver their results on the main looper, which the JVM tests do not have.
 *
 * No Hilt and no activity: the controller is built directly and called from the test thread, as
 * any caller would. The fake's simulation methods run on the main thread, the rule
 * `InAppUpdateE2ETest` also follows, because they notify listeners synchronously on the calling
 * thread. The fake never launches the intent, so a stub launcher is enough.
 */
@RunWith(AndroidJUnit4::class)
class PlayAppUpdateControllerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var fakeAppUpdateManager: FakeAppUpdateManager
    private lateinit var controller: PlayAppUpdateController

    @Before
    fun setup() {
        fakeAppUpdateManager = FakeAppUpdateManager(instrumentation.targetContext)
        controller = PlayAppUpdateController(fakeAppUpdateManager)
    }

    @Test
    fun noUpdateOnPlay_shouldEmitNotAvailable() = runBlocking {
        assertThat(controller.state.firstWithTimeout()).isEqualTo(AppUpdateState.NotAvailable)
    }

    @Test
    fun flexibleUpdateOnPlay_shouldEmitAvailableWithVersionCode() = runBlocking {
        setFlexibleUpdateAvailable()

        assertThat(controller.state.firstWithTimeout())
            .isEqualTo(AppUpdateState.Available(UPDATE_VERSION_CODE))
    }

    @Test
    fun startFlexibleUpdateBeforeAnyCheck_shouldReturnFalseAndShowNothing() {
        setFlexibleUpdateAvailable()

        val launched = controller.startFlexibleUpdate(StubLauncher)

        assertThat(launched).isFalse()
        assertThat(readFake { isConfirmationDialogVisible }).isFalse()
    }

    @Test
    fun startFlexibleUpdateAfterAvailable_shouldShowConsentAndReturnTrue() = runBlocking {
        setFlexibleUpdateAvailable()
        controller.state.firstWithTimeout()

        val launched = controller.startFlexibleUpdate(StubLauncher)

        assertThat(launched).isTrue()
        assertThat(readFake { isConfirmationDialogVisible }).isTrue()
    }

    @Test
    fun startFlexibleUpdateTwiceForOneCheck_shouldReturnFalseTheSecondTime() = runBlocking {
        setFlexibleUpdateAvailable()
        controller.state.firstWithTimeout()
        controller.startFlexibleUpdate(StubLauncher)

        assertThat(controller.startFlexibleUpdate(StubLauncher)).isFalse()
    }

    @Test
    fun acceptedUpdate_shouldEmitDownloadingThenDownloaded() = runBlocking {
        setFlexibleUpdateAvailable()
        val collected = mutableListOf<AppUpdateState>()
        val collection = launch {
            controller.state.distinctUntilChanged().take(3).toList(collected)
        }
        awaitEmissions(collected, count = 1)

        controller.startFlexibleUpdate(StubLauncher)
        acceptAndDownloadUpdate()
        withTimeout(TIMEOUT_MILLIS) { collection.join() }

        assertThat(collected).containsExactly(
            AppUpdateState.Available(UPDATE_VERSION_CODE),
            AppUpdateState.Downloading,
            AppUpdateState.Downloaded,
        ).inOrder()
    }

    @Test
    fun completeUpdateWithoutDownload_shouldReturnFalse() = runBlocking {
        assertThat(controller.completeUpdate()).isFalse()
        assertThat(readFake { isInstallSplashScreenVisible }).isFalse()
    }

    @Test
    fun completeUpdateAfterDownload_shouldShowInstallScreenAndReturnTrue() = runBlocking {
        setFlexibleUpdateAvailable()
        controller.state.firstWithTimeout()
        controller.startFlexibleUpdate(StubLauncher)
        acceptAndDownloadUpdate()

        val completed = controller.completeUpdate()

        assertThat(completed).isTrue()
        assertThat(readFake { isInstallSplashScreenVisible }).isTrue()
    }

    private fun setFlexibleUpdateAvailable() = onMainThread {
        fakeAppUpdateManager.setUpdateAvailable(UPDATE_VERSION_CODE, AppUpdateType.FLEXIBLE)
    }

    /** Answers the consent sheet with "Update" and finishes the download. */
    private fun acceptAndDownloadUpdate() = onMainThread {
        fakeAppUpdateManager.userAcceptsUpdate()
        fakeAppUpdateManager.downloadStarts()
        fakeAppUpdateManager.downloadCompletes()
    }

    private suspend fun Flow<AppUpdateState>.firstWithTimeout(): AppUpdateState =
        withTimeout(TIMEOUT_MILLIS) { first() }

    /** Waits until the check has landed, so the fake is driven only after the listener exists. */
    private suspend fun awaitEmissions(collected: List<AppUpdateState>, count: Int) {
        withTimeout(TIMEOUT_MILLIS) {
            while (collected.size < count) delay(POLL_MILLIS)
        }
    }

    private fun readFake(read: FakeAppUpdateManager.() -> Boolean): Boolean {
        var result = false
        onMainThread { result = fakeAppUpdateManager.read() }
        return result
    }

    private fun onMainThread(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** The fake never launches the intent, so none of this is ever invoked. */
    private object StubLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        override val contract: ActivityResultContract<IntentSenderRequest, *>
            get() = error("never used by FakeAppUpdateManager")

        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) {
            error("never used by FakeAppUpdateManager")
        }

        override fun unregister() = Unit
    }

    private companion object {
        // any version above the installed one; Play never offers a lower version code
        val UPDATE_VERSION_CODE = BuildConfig.VERSION_CODE + 1
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 50L
    }
}
