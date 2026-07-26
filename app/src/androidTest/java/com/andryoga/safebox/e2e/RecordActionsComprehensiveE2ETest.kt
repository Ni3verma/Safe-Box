package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.models.record.BankAccountData
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
 * End-to-End (E2E) Hilt UI test suite verifying deletion, field clearing, and editing across Note, Bank Card, and Bank Account records.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecordActionsComprehensiveE2ETest {

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
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

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
    fun deleteExistingSecureNoteRecord_shouldRemoveRecordFromHomeList() {
        val targetTitle = "Note To Be Deleted"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            secureNoteDataRepository.upsertSecureNoteData(
                NoteData(
                    id = 801,
                    title = targetTitle,
                    notes = "Confidential note body",
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            val deleteDesc = context.getString(R.string.cd_action_delete)
            composeTestRule.onNodeWithContentDescription(deleteDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.confirm)).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun deleteExistingBankCardRecord_whenCancelled_shouldKeepRecordIntact() {
        val targetTitle = "Card Deletion Cancel Test"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 802,
                    title = targetTitle,
                    number = "4111222233334444",
                    name = null,
                    expiryDate = null,
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            val deleteDesc = context.getString(R.string.cd_action_delete)
            composeTestRule.onNodeWithContentDescription(deleteDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.common_cancel))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
        }
    }

    @Test
    fun deleteExistingBankAccountRecord_shouldRemoveRecordFromHomeList() {
        val targetTitle = "Account To Be Deleted"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            bankAccountDataRepository.upsertBankAccountData(
                BankAccountData(
                    id = 804,
                    title = targetTitle,
                    accountNo = "9988776655",
                    customerName = null,
                    customerId = null,
                    branchCode = null,
                    branchName = null,
                    branchAddress = null,
                    ifscCode = null,
                    micrCode = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            val deleteDesc = context.getString(R.string.cd_action_delete)
            composeTestRule.onNodeWithContentDescription(deleteDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.confirm)).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun clearOptionalNotesFieldInLoginRecord_shouldSaveAndNotRenderNotesInViewMode() {
        val targetTitle = "Login Clear Notes Test"
        val initialNotes = "Initial optional notes content"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 803,
                    title = targetTitle,
                    url = null,
                    userId = "test@domain.com",
                    password = "password123",
                    notes = initialNotes,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            // Verify initial notes shown in view mode
            composeTestRule.onNodeWithText(initialNotes).assertIsDisplayed()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(initialNotes)
                .assertIsDisplayed()
                .performTextReplacement("")
            composeTestRule.waitForIdle()

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).assertIsDisplayed()
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            // Re-open saved record in view mode from RecordsScreen
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            // Verify notes label is not rendered when empty in view mode
            composeTestRule.onNodeWithText(context.getString(R.string.notes)).assertDoesNotExist()
            composeTestRule.onNodeWithText(initialNotes).assertDoesNotExist()
        }
    }

    @Test
    fun editExistingBankCardRecord_shouldSaveAndRenderFormattedNumberInViewMode() {
        val targetTitle = "Card Formatting Test"
        val initialNumber = "4111222233334444"
        val updatedNumber = "5555666677778888"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 805,
                    title = targetTitle,
                    number = initialNumber,
                    name = "NITIN",
                    expiryDate = "1228",
                    cvv = null,
                    pin = null,
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            E2ETestUtils.unlockApp(composeTestRule, context)
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitForIdle()

            // Verify initial SpaceAfterEveryFourCharsTransformation in View Mode ("4111 2222 3333 4444") and ExpiryDateTransformation ("12/28")
            composeTestRule.onNodeWithText("4111 2222 3333 4444").assertIsDisplayed()
            composeTestRule.onNodeWithText("12/28").assertIsDisplayed()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(initialNumber)
                .assertIsDisplayed()
                .performTextReplacement(updatedNumber)
            composeTestRule.waitForIdle()

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).assertIsDisplayed()
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            // Re-open saved card in view mode from RecordsScreen
            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText("5555 6666 7777 8888")).fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeTestRule.onNodeWithText("5555 6666 7777 8888").assertIsDisplayed()
            composeTestRule.onNodeWithText("12/28").assertIsDisplayed()
        }
    }
}
