@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.net.Uri
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
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
import java.io.File
import javax.inject.Inject

/**
 * End-to-End (E2E) Hilt UI Test exercising the complete cross-feature multi-screen user journey:
 * Login -> Add Item -> Search/Filter -> Backup -> Settings -> Timeout -> Backstack Verification.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CrossFeatureUserJourneysE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

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
    fun crossFeatureFullUserJourney_loginToAddItemToSearchToBackupToSettingsToTimeout_shouldVerifyAllStatesCleanly() {
        val backupDir = File(context.cacheDir, "e2e_journey_backup")
        backupDir.deleteRecursively()
        backupDir.mkdirs()

        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository
            )
            E2ETestUtils.setupSettingsState(
                settingsDataStore,
                isPrivacyEnabled = true,
                awayTimeoutSec = 10
            )
            E2ETestUtils.setupBackupMetadataState(
                backupMetadataRepository,
                mockUriString = Uri.fromFile(backupDir).toString(),
                mockTimestamp = System.currentTimeMillis()
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Step 1: Unlock App (Login -> Home)
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(hasText("Apple ID Login"), useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()

            // Step 2: Add New Record (Add Item)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                R.string.type_display_note
            )
            composeTestRule.waitForIdle()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(context.getString(R.string.title), substring = true)
            ).assertIsDisplayed()
                .performTextInput("E2E Test Secret Note")

            composeTestRule.onNode(
                hasSetTextAction() and hasText(context.getString(R.string.notes), substring = true)
            ).assertIsDisplayed()
                .performTextInput("Verified multi-screen user journey note content")

            composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            // Confirm new note appears on Records screen
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText("E2E Test Secret Note"),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("E2E Test Secret Note").assertIsDisplayed()

            // Step 3: Search & Filter (Search/Filter)
            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .performTextInput("E2E Test")
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        hasText("E2E Test Secret Note"),
                        useUnmergedTree = true
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("E2E Test Secret Note").assertIsDisplayed()
            composeTestRule.onNodeWithText("Apple ID Login").assertDoesNotExist()

            composeTestRule.waitForIdle()
            val clearDesc = context.getString(R.string.cd_clear_search_bar)
            composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true)
                .onParent()
                .performClick()
            composeTestRule.waitForIdle()

            runCatching {
                val searchInputNodes = composeTestRule.onAllNodes(
                    hasSetTextAction() and hasText("E2E Test"),
                    useUnmergedTree = true
                ).fetchSemanticsNodes()
                if (searchInputNodes.isNotEmpty()) {
                    composeTestRule.onNode(
                        hasSetTextAction() and hasText("E2E Test"),
                        useUnmergedTree = true
                    ).performTextReplacement("")
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(hasText("Apple ID Login"), useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()

            // Step 4: Backup (Navigate and trigger backup creation)
            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_backup_and_restore))
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.backup_set_message))
                .assertIsDisplayed()
            composeTestRule.onNode(hasText(context.getString(R.string.backup)) and hasClickAction())
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.new_backup_dialog_body_text))
                .assertIsDisplayed()
            val passwordTextFields = composeTestRule.onAllNodes(hasSetTextAction())
            passwordTextFields[0].performTextReplacement(E2ETestUtils.TEST_MASTER_PASSWORD)

            composeTestRule.onNodeWithText(context.getString(R.string.confirm))
                .performClick()

            // Wait until dialog closes or backup completes
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    backupDir.listFiles { f ->
                        f.name.startsWith("SafeBoxBackup") && f.name.endsWith(
                            ".bak"
                        )
                    }?.isNotEmpty() == true
                }.getOrDefault(false)
            }
            assertThat(backupDir.listFiles { f ->
                f.name.startsWith("SafeBoxBackup") && f.name.endsWith(
                    ".bak"
                )
            }?.isNotEmpty()).isTrue()

            // Close dialog if still shown on success
            runCatching {
                composeTestRule.onNodeWithText(context.getString(R.string.common_ok)).performClick()
                composeTestRule.waitForIdle()
            }

            // Step 5: Settings (Theme/Privacy Toggles & Window Flag Check)
            composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_settings))
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.settings_section_security_and_privacy))
                .assertIsDisplayed()

            // Verify initial FLAG_SECURE from pre-seeded isPrivacyEnabled = true
            scenario.onActivity { activity ->
                assertThat((activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0).isTrue()
            }

            // Toggle privacy off and verify FLAG_SECURE is cleared immediately via StateFlow
            composeTestRule.onAllNodes(isToggleable())[0].performClick()
            composeTestRule.waitForIdle()
            scenario.onActivity { activity ->
                assertThat((activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0).isTrue()
            }

            // Step 6: Session Timeout (Trigger UserAwayDialog & verify backstack pop)
            activeSessionManager.onStop(object : androidx.lifecycle.LifecycleOwner {
                override val lifecycle: androidx.lifecycle.Lifecycle
                    get() = androidx.lifecycle.LifecycleRegistry(this)
            })

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(hasText(context.getString(R.string.timeout_dialog_message)))
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.timeout_dialog_message))
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.timeout_dialog_positive_button_text))
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(hasText(context.getString(R.string.welcome_back)))
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            // Verify Backstack Security: HomeGraph is popped completely upon logout/timeout transition
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertDoesNotExist()
        }
    }
}
