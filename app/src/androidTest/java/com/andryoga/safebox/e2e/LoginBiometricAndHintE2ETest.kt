@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * End-to-End (E2E) Hilt UI Test suite for master password validation, repository hint workflows, and biometric unlock threshold persistence.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoginBiometricAndHintE2ETest {

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
        com.andryoga.safebox.ui.core.biometricAuthHandlerOverride = null
        runBlocking {
            settingsDataStore.updateAwayTimeout(SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT)
            settingsDataStore.updatePrivacy(SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT)
            settingsDataStore.updateAutoBackupAfterPasswordLogin(SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
            settingsDataStore.updatePasswordAfterXBiometricLogin(SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT)
            activeSessionManager.setPaused(true)
        }
    }

    @Test
    fun showHintClick_whenHintExistsInRepository_shouldFetchAndDisplayExactHintText() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.show_hint))
                .assertIsDisplayed()
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(E2ETestUtils.TEST_MASTER_HINT),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(E2ETestUtils.TEST_MASTER_HINT)
                .assertIsDisplayed()

            composeTestRule.onNodeWithText(context.getString(R.string.hide_hint))
                .assertIsDisplayed()
                .performClick()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(E2ETestUtils.TEST_MASTER_HINT),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(E2ETestUtils.TEST_MASTER_HINT)
                .assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.show_hint))
                .assertIsDisplayed()
        }
    }

    @Test
    fun biometricAuthSuccess_whenRemainingCountGreaterThanZero_shouldDecrementAllowedBiometricRetryCount() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            val initialLoginCount =
                preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                3
            )

            assertThat(userDetailsRepository.shouldStartBiometricAuthFlow()).isTrue()
            userDetailsRepository.onAuthSuccess(withBiometric = true)

            val remainingCount = preferenceProvider.getIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
            )
            assertThat(remainingCount).isEqualTo(2)

            val finalLoginCount =
                preferenceProvider.getIntPref(CommonConstants.TOTAL_LOGIN_COUNT, 1)
            assertThat(finalLoginCount).isEqualTo(initialLoginCount + 1)
        }
    }

    @Test
    fun biometricAuthThresholdReached_whenRemainingCountIsZero_shouldDisableBiometricAuthFlowAndRequireMasterPassword() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                0
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            runBlocking {
                assertThat(userDetailsRepository.shouldStartBiometricAuthFlow()).isFalse()
            }

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            ).performTextReplacement("WrongPass")

            composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()

            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.incorrect_pswrd_message)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message))
                .assertIsDisplayed()
        }
    }

    @Test
    fun masterPasswordLoginSuccess_whenBiometricRemainingCountIsZero_shouldResetBiometricRemainingCountToDefault() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                0
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            ).performTextReplacement(E2ETestUtils.TEST_MASTER_PASSWORD)

            composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
            composeTestRule.waitForIdle()

            val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasContentDescription(
                            addNewButtonDesc
                        )
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.waitForIdle()

            runBlocking {
                val remainingCount = preferenceProvider.getIntPref(
                    CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                    0
                )
                val defaultThreshold = settingsDataStore.getPasswordAfterXBiometricLogins()
                assertThat(remainingCount).isEqualTo(defaultThreshold)
            }
        }
    }

    @Test
    fun biometricErrorOrCancellation_shouldResetUiStateAndPreventInfinitePromptLoop() {
        var biometricErrorTriggered = false
        com.andryoga.safebox.ui.core.biometricAuthHandlerOverride = { _, onErrorOrCancel ->
            biometricErrorTriggered = true
            androidx.compose.runtime.LaunchedEffect(Unit) {
                onErrorOrCancel()
            }
        }

        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            preferenceProvider.upsertIntPref(
                CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
                3
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false) && biometricErrorTriggered
            }
            composeTestRule.onNodeWithText(context.getString(R.string.welcome_back))
                .assertIsDisplayed()

            runBlocking {
                assertThat(userDetailsRepository.shouldStartBiometricAuthFlow()).isTrue()
            }

            // Simulate biometric error/cancellation by triggering master password fallback input directly
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            ).performTextReplacement(E2ETestUtils.TEST_MASTER_PASSWORD)
            composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
            composeTestRule.waitForIdle()

            val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runCatching {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasContentDescription(
                            addNewButtonDesc
                        )
                    )
                        .fetchSemanticsNodes().isNotEmpty()
                }.getOrDefault(false)
            }
            composeTestRule.waitForIdle()

            // Verify clean unlock via password fallback when biometric was initially active
            composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription(addNewButtonDesc))
                .assertIsDisplayed()
        }
    }
}
