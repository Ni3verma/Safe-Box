@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

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
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
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
 * End-to-End (E2E) Hilt UI Test suite verifying creation flows across supported record types.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddNewRecordFlowsE2ETest {

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
    fun addNewLoginRecord_shouldSaveAndAppearInRecordsList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }
        val targetTitle = "E2E Login Title"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                R.string.type_display_login
            )

            // Fill in mandatory fields on SingleRecordScreen
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextInput(targetTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.user_id),
                    substring = true
                )
            )
                .performTextInput("user@test.com")
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.password),
                    substring = true
                )
            )
                .performTextInput("SecretPass!123")

            // Click Save and verify item appears on RecordsScreen
            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(
                    hasText(targetTitle, substring = true),
                    useUnmergedTree = true
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
        }
    }

    @Test
    fun addNewNoteRecord_shouldSaveAndAppearInRecordsList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }
        val targetTitle = "E2E Note Title"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                R.string.type_display_note
            )

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextInput(targetTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.notes),
                    substring = true
                )
            )
                .performTextInput("Very secret notes content for testing")

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(
                    hasText(targetTitle, substring = true),
                    useUnmergedTree = true
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
        }
    }

    @Test
    fun addNewBankCardRecord_shouldSaveAndAppearInRecordsList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }
        val targetTitle = "E2E Card Title"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                R.string.type_display_card
            )

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextInput(targetTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.number),
                    substring = true
                )
            )
                .performTextInput("4111111111111111")

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(
                    hasText(targetTitle, substring = true),
                    useUnmergedTree = true
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
        }
    }

    @Test
    fun addNewBankAccountRecord_shouldSaveAndAppearInRecordsList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }
        val targetTitle = "E2E Bank Account Title"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                R.string.type_display_account
            )

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextInput(targetTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.account_number),
                    substring = true
                )
            )
                .performTextInput("0987654321")

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(
                    hasText(targetTitle, substring = true),
                    useUnmergedTree = true
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
        }
    }
}
