@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
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
import javax.inject.Inject

/**
 * End-to-End (E2E) Hilt UI Test suite verifying bottom navigation switching, warning badge
 * rendering, the away timeout (lock, banner, back stack, re-login) and the HomeGraph gate.
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
    lateinit var preferenceProvider: PreferenceProvider

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

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

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

    /**
     * Journey E1 / J5: the away timeout clears the session key, the gate leaves for Login, the
     * banner says why, back cannot return to Home, and a password login brings the list back.
     */
    @Test
    fun awayTimeout_whileOnRecords_shouldLockToLoginWithBannerAndRestoreTheListAfterPasswordLogin() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository,
            )
            settingsDataStore.updateAwayTimeout(0)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockAppAndWaitForTitle(composeTestRule, context, SEEDED_RECORD_TITLE)

            activeSessionManager.onStop(E2ETestUtils.createTestLifecycleOwner())

            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.login_away_banner)))
            composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(SEEDED_RECORD_TITLE).assertDoesNotExist()

            // A password login brings the list back, and the banner does not follow it to Home.
            E2ETestUtils.unlockAppAndWaitForTitle(composeTestRule, context, SEEDED_RECORD_TITLE)
            composeTestRule.onNodeWithText(SEEDED_RECORD_TITLE).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner))
                .assertDoesNotExist()

            // A second timeout, then back: the HomeGraph was popped, so Login is the root and back
            // leaves the app instead of revealing the records.
            activeSessionManager.onStop(E2ETestUtils.createTestLifecycleOwner())
            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.login_away_banner)))
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            // Holds whether the Activity finished (no hierarchy) or not; fails only if back
            // revealed Home. Nothing but assertDoesNotExist can run after this back press.
            composeTestRule.onNodeWithText(SEEDED_RECORD_TITLE).assertDoesNotExist()
        }
    }

    /**
     * The HomeGraph gate. Process death keeps Home on the saved navigation state but
     * empties the RAM-only session holder; `lockVault()` + `recreate()` reproduces exactly that
     * pair, and the restored Home must leave for Login without ever showing the vault. No reason
     * was recorded, so no banner either.
     */
    @Test
    fun recreateIntoHome_whenTheVaultIsLocked_shouldLeaveForLoginWithoutShowingRecordsOrBanner() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertIsDisplayed()

            VaultSessionTestBridge.lockVault()
            scenario.recreate()

            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back)).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner))
                .assertDoesNotExist()

            // Back must not reveal a Home that was never meant to be composed.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertDoesNotExist()
        }
    }

    @Test
    fun recreateIntoHome_whileTheSessionIsOpen_shouldStayOnHome() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            scenario.recreate()

            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.no_record)))
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back)).assertDoesNotExist()
        }
    }

    /**
     * The same gate while Home is on screen: whoever clears the holder, Home leaves for Login on
     * its own, with no Activity recreation and no second navigator. An explicit lock records no
     * reason, so the banner stays away.
     */
    @Test
    fun lockVault_whileHomeIsShowing_shouldLeaveForLoginWithoutBanner() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertIsDisplayed()

            VaultSessionTestBridge.lockVault()

            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner))
                .assertDoesNotExist()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record)).assertDoesNotExist()
        }
    }

    /**
     * The window the gate cannot close: an operation that starts after the clear but before the
     * next frame navigates. The lock and the Save tap are dispatched in one main-thread message,
     * so the save begins against a locked holder and fails closed before the gate has run. The
     * app must not crash, Login must follow, and the record must not exist afterwards.
     */
    @Test
    fun lockVault_inTheSameFrameAsSave_shouldNotCrashAndLeaveForLoginWithoutSaving() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            fillNewNoteForm()

            tapSaveOnTheMainThread(scenario, lockVaultFirst = true)

            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
            composeTestRule.onNodeWithText(NOTE_TITLE).assertDoesNotExist()

            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.no_record)))
            composeTestRule.onNodeWithText(NOTE_TITLE).assertDoesNotExist()
        }
    }

    /** Control for the test above: the same raw tap saves the record when nothing locks. */
    @Test
    fun tapSaveOnTheMainThread_withoutALock_shouldSaveTheRecord() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            fillNewNoteForm()

            tapSaveOnTheMainThread(scenario, lockVaultFirst = false)

            E2ETestUtils.waitForRecordTitle(composeTestRule, NOTE_TITLE)
            composeTestRule.onNodeWithText(NOTE_TITLE).assertIsDisplayed()
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
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                var isFlagCleared = false
                scenario.onActivity { activity ->
                    val flags = activity.window.attributes.flags
                    isFlagCleared = (flags and WindowManager.LayoutParams.FLAG_SECURE) == 0
                }
                isFlagCleared
            }

            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertThat((flags and WindowManager.LayoutParams.FLAG_SECURE) == 0).isTrue()
            }
        }
    }

    /** Opens the create screen for a note and fills both mandatory fields, so Save is enabled. */
    private fun fillNewNoteForm() {
        E2ETestUtils.clickAddNewRecordOption(composeTestRule, context, R.string.type_display_note)
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.title), substring = true),
        ).performTextInput(NOTE_TITLE)
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.notes), substring = true),
        ).performTextInput("Typed right before the vault locked")
        E2ETestUtils.closeSoftKeyboard(composeTestRule, context)
        composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()
    }

    /**
     * Taps Save by injecting the touch events on the main thread, optionally clearing the session
     * key in the same message. `performClick` cannot do this: it synchronises with Compose first,
     * and the pending recomposition would run the gate before the tap lands.
     */
    private fun tapSaveOnTheMainThread(
        scenario: ActivityScenario<MainActivity>,
        lockVaultFirst: Boolean,
    ) {
        val saveCentre = composeTestRule.onNodeWithText(context.getString(R.string.save))
            .fetchSemanticsNode().boundsInWindow.center
        scenario.onActivity { activity ->
            if (lockVaultFirst) VaultSessionTestBridge.lockVault()
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, saveCentre.x, saveCentre.y, 0,
            )
            val up = MotionEvent.obtain(
                downTime, downTime + TAP_DURATION_MS, MotionEvent.ACTION_UP, saveCentre.x, saveCentre.y, 0,
            )
            try {
                activity.dispatchTouchEvent(down)
                activity.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }
    }

    private companion object {
        const val SEEDED_RECORD_TITLE = "Apple ID Login"
        const val NOTE_TITLE = "Note saved under a closing vault"
        const val TAP_DURATION_MS = 50L
    }
}
