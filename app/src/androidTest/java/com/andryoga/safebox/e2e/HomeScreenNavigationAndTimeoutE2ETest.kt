@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
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

/**
 * End-to-End (E2E) Hilt UI Test suite verifying bottom navigation switching, warning badge rendering, and user timeout dialog handling.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenNavigationAndTimeoutE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @Inject
    lateinit var preferenceProvider: com.andryoga.safebox.providers.interfaces.PreferenceProvider

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var backupMetadataRepository: BackupMetadataRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var activeSessionManager: ActiveSessionManager

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        runBlocking {
            settingsDataStore.updateAwayTimeout(SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT)
            settingsDataStore.updatePrivacy(SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT)
            settingsDataStore.updateAutoBackupAfterPasswordLogin(SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
            settingsDataStore.updatePasswordAfterXBiometricLogin(SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT)
            activeSessionManager.setPaused(true)
        }
    }

    @Test
    fun clickBottomNavItem_whenOnRecordsScreen_shouldNavigateToSettingsAndBackupAndRestore() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_settings))
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.settings_section_security_and_privacy)),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.settings_section_security_and_privacy))
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_backup_and_restore))
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.backup_path_not_set_message)),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.backup_path_not_set_message))
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_records))
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.no_record)),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertIsDisplayed()
        }
    }

    @Test
    fun backupPathNotSet_shouldRenderWarningBadgeOnBackupAndRestoreBottomNavItem() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            backupMetadataRepository.deleteBackupMetadata()
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_backup_and_restore))
                .assertIsDisplayed()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(hasText("!"), useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNode(hasText("!"), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Test
    fun userTimeoutOrLogoutEvent_whenTriggeredFromMainViewModel_shouldShowUserAwayDialogAndExitHomeOnConfirm() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            settingsDataStore.updateAwayTimeout(0)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            activeSessionManager.onStop(object : androidx.lifecycle.LifecycleOwner {
                override val lifecycle: androidx.lifecycle.Lifecycle
                    get() = androidx.lifecycle.LifecycleRegistry(this)
            })

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(androidx.compose.ui.test.hasText(context.getString(R.string.timeout_dialog_message)))
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.timeout_dialog_message))
                .assertIsDisplayed()

            runCatching {
                composeTestRule.onRoot(useUnmergedTree = true).performTouchInput {
                    click(Offset(10f, 10f))
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.timeout_dialog_message))
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.timeout_dialog_positive_button_text))
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)))
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            // Verify backstack security: HomeGraph must be completely cleared so pressing back does not return to HomeScreen
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertDoesNotExist()
        }
    }

    @Test
    fun privacyFlagEnabled_shouldSetFlagSecureOnWindow() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            settingsDataStore.updatePrivacy(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertThat((flags and WindowManager.LayoutParams.FLAG_SECURE) != 0).isTrue()
            }
        }
    }

    @Test
    fun privacyFlagDisabled_shouldClearFlagSecureFromWindow() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            settingsDataStore.updatePrivacy(false)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertThat((flags and WindowManager.LayoutParams.FLAG_SECURE) == 0).isTrue()
            }
        }
    }
}
