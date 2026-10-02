@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.BuildConfig
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import androidx.compose.material3.R as Material3R

/**
 * End-to-end tests for the in-app update flow, driven by Play's [FakeAppUpdateManager] through the
 * real `PlayAppUpdateController`. `FakeAppUpdateModule` swaps it in for all instrumentation tests.
 *
 * The fake does not start Play's activity. Instead it flips its visibility flags, which is what
 * these tests assert on. Fake state is only touched on the main thread, where the app reads it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class InAppUpdateE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var fakeAppUpdateManager: FakeAppUpdateManager

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var activeSessionManager: ActiveSessionManager

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase = safeBoxDatabase,
                userDetailsRepository = userDetailsRepository,
                encryptedPreferenceProvider = encryptedPreferenceProvider,
                preferenceProvider = preferenceProvider,
                settingsDataStore = settingsDataStore,
            )
            // the prompted version survives in shared preferences, so an earlier test would
            // otherwise suppress the prompt for the same version
            preferenceProvider.removePrefByKey(CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE)
        }
        onMainThread {
            fakeAppUpdateManager.setUpdateAvailable(UPDATE_VERSION_CODE, AppUpdateType.FLEXIBLE)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            preferenceProvider.removePrefByKey(CommonConstants.IN_APP_UPDATE_PROMPTED_VERSION_CODE)
            E2ETestUtils.resetAppState(
                safeBoxDatabase = safeBoxDatabase,
                settingsDataStore = settingsDataStore,
                activeSessionManager = activeSessionManager,
                encryptedPreferenceProvider = encryptedPreferenceProvider,
            )
        }
    }

    @Test
    fun updateAvailable_afterLogin_shouldShowPlayConsent() {
        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)

            waitUntilFake { isConfirmationDialogVisible }
        }
    }

    @Test
    fun updateAvailable_onLoginScreen_shouldNotPrompt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.welcome_back))
            composeTestRule.waitForIdle()

            assertThat(readFake { isConfirmationDialogVisible }).isFalse()
        }
    }

    @Test
    fun acceptAndDownload_shouldShowRestartSnackbar() {
        ActivityScenario.launch(MainActivity::class.java).use {
            acceptAndDownloadUpdate()

            composeTestRule.onNodeWithText(context.getString(R.string.in_app_update_restart))
                .assertExists()
        }
    }

    @Test
    fun restartSnackbarAction_shouldCompleteUpdate() {
        ActivityScenario.launch(MainActivity::class.java).use {
            acceptAndDownloadUpdate()

            composeTestRule.onNodeWithText(context.getString(R.string.in_app_update_restart))
                .performClick()

            waitUntilFake { isInstallSplashScreenVisible }
        }
    }

    @Test
    fun restartSnackbarDismiss_shouldHideSnackbar() {
        ActivityScenario.launch(MainActivity::class.java).use {
            acceptAndDownloadUpdate()

            composeTestRule.onNodeWithContentDescription(
                context.getString(Material3R.string.m3c_snackbar_dismiss),
            ).performClick()

            composeTestRule.waitUntil(TIMEOUT_MILLIS) {
                composeTestRule.onAllNodes(hasText(downloadedMessage()))
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
            assertThat(readFake { isInstallSplashScreenVisible }).isFalse()
        }
    }

    @Test
    fun downloadedThenAwayTimeout_shouldCompleteUpdateSilently() {
        runBlocking { settingsDataStore.updateAwayTimeout(0) }
        ActivityScenario.launch(MainActivity::class.java).use {
            acceptAndDownloadUpdate()

            // a zero timeout fires the logout event as soon as the app stops
            activeSessionManager.onStop(E2ETestUtils.createTestLifecycleOwner())

            waitUntilFake { isInstallSplashScreenVisible }
        }
    }

    /**
     * Logs in, answers Play's consent sheet with "Update" and completes the download, then waits
     * for the restart snackbar.
     */
    private fun acceptAndDownloadUpdate() {
        E2ETestUtils.unlockApp(composeTestRule, context)
        waitUntilFake { isConfirmationDialogVisible }
        onMainThread {
            fakeAppUpdateManager.userAcceptsUpdate()
            fakeAppUpdateManager.downloadStarts()
            fakeAppUpdateManager.downloadCompletes()
        }
        E2ETestUtils.waitForText(composeTestRule, downloadedMessage())
    }

    private fun downloadedMessage(): String = context.getString(R.string.in_app_update_downloaded)

    private fun waitUntilFake(condition: FakeAppUpdateManager.() -> Boolean) {
        composeTestRule.waitUntil(TIMEOUT_MILLIS) { readFake(condition) }
    }

    private fun readFake(read: FakeAppUpdateManager.() -> Boolean): Boolean {
        var result = false
        onMainThread { result = fakeAppUpdateManager.read() }
        return result
    }

    private fun onMainThread(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private companion object {
        // any version above the installed one; Play never offers a lower version code
        val UPDATE_VERSION_CODE = BuildConfig.VERSION_CODE + 1
        const val TIMEOUT_MILLIS = 25_000L
    }
}
