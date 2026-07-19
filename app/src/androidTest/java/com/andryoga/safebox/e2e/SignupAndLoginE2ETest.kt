@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
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
 * End-to-End (E2E) Full Stack UI Integration Tests for SafeBox Signup and Login flows.
 *
 * Exercises the entire application architecture:
 * - MainActivity & AppNavigation / NavHost routing
 * - Hilt Dependency Injection (@HiltAndroidTest)
 * - SharedPreferences / EncryptedPreferenceProvider persistence (IS_SIGN_UP_REQUIRED flag)
 * - Room Database (UserDetailsRepository / UserDetailsDaoSecure / SafeBoxDatabase)
 * - Multi-screen navigation (Signup -> Home (0 records) and Login -> Home)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SignupAndLoginE2ETest {

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
    fun firstLaunch_signupFlow_shouldPersistCredentialsAndNavigateToHomeWithZeroRecords() {
        // Given: Fresh app state with zero records and signup required
        runBlocking {
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
            safeBoxDatabase.clearAllTables()
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            // 1. Verify Signup Screen is displayed on first boot
            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome))
                .assertIsDisplayed()

            // 2. Enter valid master password and hint (using performTextReplacement to override any debug pre-fills)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            )
                .performTextReplacement(E2ETestUtils.TEST_MASTER_PASSWORD)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.hint),
                    substring = true
                )
            )
                .performTextReplacement(E2ETestUtils.TEST_MASTER_HINT)
            composeTestRule.waitForIdle()
            E2ETestUtils.closeSoftKeyboard(composeTestRule, context)
            composeTestRule.waitForIdle()

            // 3. Click Sign Up
            composeTestRule.onNodeWithText(context.getString(R.string.signup))
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            // 4. Verify clean transition to Home screen showing 0 records state
            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.no_record)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertIsDisplayed()

            // 5. Verify database and preference persistence directly from injected singletons
            runBlocking {
                val isSignupStillRequired = encryptedPreferenceProvider.getBooleanPref(
                    CommonConstants.IS_SIGN_UP_REQUIRED,
                    true
                )
                assertThat(isSignupStillRequired).isFalse()

                val isPasswordCorrect =
                    userDetailsRepository.checkPassword(E2ETestUtils.TEST_MASTER_PASSWORD)
                assertThat(isPasswordCorrect).isTrue()
            }
        }
    }

    @Test
    fun subsequentLaunch_loginFlow_shouldRejectWrongPasswordAndNavigateToHomeOnCorrectPassword() {
        // Given: Pre-seeded credentials using E2ETestUtils (IS_SIGN_UP_REQUIRED = false, biometric count = 0)
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            // 1. Verify Login Screen is displayed on subsequent launch
            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            // 2. Attempt login with wrong password and verify rejection
            composeTestRule.waitForIdle()
            val passwordField = composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            )
            passwordField.performTextReplacement("WrongPass123")
            composeTestRule.waitForIdle()
            E2ETestUtils.closeSoftKeyboard(composeTestRule, context)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.incorrect_pswrd_message)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message))
                .assertIsDisplayed()

            // 3. Enter correct master password and submit via E2ETestUtils.unlockApp
            E2ETestUtils.unlockApp(composeTestRule, context)

            // 4. Verify successful authentication unlocks and opens Home screen showing 0 records
            composeTestRule.waitUntil(timeoutMillis = 25000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.no_record)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertIsDisplayed()
        }
    }
}
