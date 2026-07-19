@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
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
import java.util.Date
import javax.inject.Inject

/**
 * End-to-End (E2E) Hilt UI Test suite verifying real-time search filtering, record type chip selection, and empty state transitions.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecordsSearchAndFilterE2ETest {

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
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var activeSessionManager: ActiveSessionManager

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

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
    fun typeSearchTextInTopBar_whenPopulatedListDisplayed_shouldFilterLazyColumnByTitleIgnoreCase() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 801,
                    title = "Amazon Shopping",
                    url = null,
                    userId = "user@amazon.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 802,
                    title = "American Express Card",
                    name = null,
                    number = "378282246310005",
                    expiryDate = null,
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            secureNoteDataRepository.upsertSecureNoteData(
                NoteData(
                    id = 803,
                    title = "Personal Diary Note",
                    notes = "secret notes",
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Amazon Shopping")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("American Express Card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Personal Diary Note").assertIsDisplayed()

            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .performTextInput("amazon")
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("American Express Card")).fetchSemanticsNodes()
                    .isEmpty()
            }

            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("American Express Card").assertDoesNotExist()
            composeTestRule.onNodeWithText("Personal Diary Note").assertDoesNotExist()
        }
    }

    @Test
    fun clickClearIconInSearchBar_whenFilterIsActive_shouldResetSearchQueryAndRestoreFullRecordList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 801,
                    title = "Amazon Shopping",
                    url = null,
                    userId = "user@amazon.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 802,
                    title = "American Express Card",
                    name = null,
                    number = "378282246310005",
                    expiryDate = null,
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            secureNoteDataRepository.upsertSecureNoteData(
                NoteData(
                    id = 803,
                    title = "Personal Diary Note",
                    notes = "secret notes",
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Amazon Shopping")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .performTextInput("amazon")
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("American Express Card")).fetchSemanticsNodes()
                    .isEmpty()
            }

            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("American Express Card").assertDoesNotExist()
            composeTestRule.onNodeWithText("Personal Diary Note").assertDoesNotExist()

            val clearDesc = context.getString(R.string.cd_clear_search_bar)
            composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true)
                .onParent()
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("American Express Card")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("American Express Card").assertIsDisplayed()
            composeTestRule.onNodeWithText("Personal Diary Note").assertIsDisplayed()
        }
    }

    @Test
    fun selectRecordTypeFilterChip_whenMultipleRecordTypesExist_shouldDisplayOnlyMatchingRecordTypes() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 811,
                    title = "Login Item",
                    url = null,
                    userId = "user@login.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 812,
                    title = "Card Item",
                    name = null,
                    number = "1122334455667788",
                    expiryDate = null,
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            secureNoteDataRepository.upsertSecureNoteData(
                NoteData(
                    id = 813,
                    title = "Note Item",
                    notes = "secret note",
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Login Item")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Login Item").assertIsDisplayed()
            composeTestRule.onNodeWithText("Card Item").assertIsDisplayed()
            composeTestRule.onNodeWithText("Note Item").assertIsDisplayed()

            val cardChipLabel = context.getString(R.string.type_display_card)
            composeTestRule.onNode(hasText(cardChipLabel) and hasRole(Role.Checkbox)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Login Item")).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText("Card Item").assertIsDisplayed()
            composeTestRule.onNodeWithText("Login Item").assertDoesNotExist()
            composeTestRule.onNodeWithText("Note Item").assertDoesNotExist()

            composeTestRule.onNode(hasText(cardChipLabel) and hasRole(Role.Checkbox)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Login Item")).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Login Item").assertIsDisplayed()
            composeTestRule.onNodeWithText("Card Item").assertIsDisplayed()
            composeTestRule.onNodeWithText("Note Item").assertIsDisplayed()
        }
    }

    @Test
    fun searchQueryOrFilterWithZeroMatches_whenRecordsExistInDb_shouldDisplayFilteredOutEmptyState() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 821,
                    title = "Amazon Shopping",
                    url = null,
                    userId = "user@amazon.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Amazon Shopping")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()

            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .performTextInput("ZebraX")
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.no_filtered_record_title)))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(context.getString(R.string.no_filtered_record_title))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.no_filtered_record_body))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertDoesNotExist()
        }
    }

    @Test
    fun searchFilterAndCategoryChipCombined_shouldDisplayOnlyItemsMatchingBothCriteriaAndRestoreOnClear() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 831,
                    title = "Apple ID Login",
                    url = null,
                    userId = "user@apple.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 832,
                    title = "Amazon Shopping",
                    url = null,
                    userId = "user@amazon.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )

            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 833,
                    title = "Chase Sapphire Card",
                    name = null,
                    number = "4111222233334444",
                    expiryDate = null,
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Apple ID Login")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()
            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("Chase Sapphire Card").assertIsDisplayed()

            // 1. Enter search text matching one login item
            composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.search_bar_placeholder)))
                .performTextInput("apple")
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Amazon Shopping")).fetchSemanticsNodes()
                    .isEmpty()
            }

            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()
            composeTestRule.onNodeWithText("Amazon Shopping").assertDoesNotExist()
            composeTestRule.onNodeWithText("Chase Sapphire Card").assertDoesNotExist()

            // 2. Select Card category chip: zero items match both "apple" AND Card
            val cardChipLabel = context.getString(R.string.type_display_card)
            composeTestRule.onNode(hasText(cardChipLabel) and hasRole(Role.Checkbox)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(context.getString(R.string.no_filtered_record_title)))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(context.getString(R.string.no_filtered_record_title))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Apple ID Login").assertDoesNotExist()

            // 3. Untoggle Card chip and select Login chip: "apple" AND Login matches "Apple ID Login"
            composeTestRule.onNode(hasText(cardChipLabel) and hasRole(Role.Checkbox)).performClick()
            val loginChipLabel = context.getString(R.string.type_display_login)
            composeTestRule.onNode(hasText(loginChipLabel) and hasRole(Role.Checkbox))
                .performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Apple ID Login")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()
            composeTestRule.onNodeWithText("Amazon Shopping").assertDoesNotExist()

            // 4. Clear search string while Login chip remains active: both Login items appear
            val clearDesc = context.getString(R.string.cd_clear_search_bar)
            composeTestRule.onNodeWithContentDescription(clearDesc, useUnmergedTree = true)
                .onParent().performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("Amazon Shopping")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("Apple ID Login").assertIsDisplayed()
            composeTestRule.onNodeWithText("Amazon Shopping").assertIsDisplayed()
            composeTestRule.onNodeWithText("Chase Sapphire Card").assertDoesNotExist()
        }
    }
}
