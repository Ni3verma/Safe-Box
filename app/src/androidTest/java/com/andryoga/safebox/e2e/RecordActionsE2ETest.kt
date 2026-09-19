@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.models.record.AuthenticatorData
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.totp.models.TotpConfig
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
import java.util.Date
import javax.inject.Inject

/**
 * End-to-End (E2E) Hilt UI Test suite verifying editing, deleting, and back-navigation workflows for existing records.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecordActionsE2ETest {

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
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

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
    fun editExistingLoginRecord_shouldSaveUpdatedTitleAndReflectInRecordsList() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            val initialTitle = "Original Login Record"
            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 701,
                    title = initialTitle,
                    url = null,
                    userId = "user@test.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        val initialTitle = "Original Login Record"
        val updatedTitle = "Updated Login Record"
        val updatedUserId = "updated@user.com"

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(initialTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(initialTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).performClick()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextReplacement(updatedTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.user_id),
                    substring = true
                )
            )
                .performTextReplacement(updatedUserId)

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(updatedTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(updatedUserId).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).assertDoesNotExist()
        }
    }

    @Test
    fun editExistingNoteRecord_shouldSaveUpdatedTitleAndNotesAndReflectInRecordsList() {
        val initialTitle = "Original Note Record"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            secureNoteDataRepository.upsertSecureNoteData(
                NoteData(
                    id = 711,
                    title = initialTitle,
                    notes = "old notes",
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        val updatedTitle = "Updated Note Record"
        val updatedNotes = "Updated confidential notes body"

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(initialTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(initialTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).performClick()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextReplacement(updatedTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.notes),
                    substring = true
                )
            )
                .performTextReplacement(updatedNotes)

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(updatedTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).assertDoesNotExist()
        }
    }

    @Test
    fun editExistingBankCardRecord_shouldSaveUpdatedTitleAndNumberAndReflectInRecordsList() {
        val initialTitle = "Original Card Record"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            bankCardDataRepository.upsertBankCardData(
                CardData(
                    id = 712,
                    title = initialTitle,
                    number = "CARD-ORIGINAL-1234",
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

        val updatedTitle = "Updated Card Record"
        val updatedNumber = "CARD-UPDATED-5678"

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(initialTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(initialTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).performClick()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextReplacement(updatedTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.number),
                    substring = true
                )
            )
                .performTextReplacement(updatedNumber)

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(updatedTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).assertDoesNotExist()
        }
    }

    @Test
    fun editExistingBankAccountRecord_shouldSaveUpdatedTitleAndAccountNumberAndReflectInRecordsList() {
        val initialTitle = "Original Account Record"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            bankAccountDataRepository.upsertBankAccountData(
                BankAccountData(
                    id = 713,
                    title = initialTitle,
                    accountNo = "1122334455",
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

        val updatedTitle = "Updated Account Record"
        val updatedAccountNumber = "9988776655"

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(initialTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(initialTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).performClick()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextReplacement(updatedTitle)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.account_number),
                    substring = true
                )
            )
                .performTextReplacement(updatedAccountNumber)

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(updatedTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).assertDoesNotExist()
        }
    }

    @Test
    fun deleteExistingLoginRecord_shouldRemoveRecordFromList() {
        val targetTitle = "Login Record To Delete"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 702,
                    title = targetTitle,
                    url = null,
                    userId = "delete@test.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()

            val deleteDesc = context.getString(R.string.cd_action_delete)
            composeTestRule.onNodeWithContentDescription(deleteDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()

            composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
                .assertIsDisplayed()
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
    fun backButtonClickInEditMode_shouldPopBackToRecordsScreenWithoutSaving() {
        val targetTitle = "Record For Back Test"
        val uncommittedTitle = "Uncommitted Edit Title"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 703,
                    title = targetTitle,
                    url = null,
                    userId = "back@test.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()
            composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsDisplayed()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            )
                .performTextReplacement(uncommittedTitle)

            val backDesc = context.getString(R.string.cd_back_button)
            composeTestRule.onNodeWithContentDescription(backDesc).assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(backDesc).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.search_bar_placeholder))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(uncommittedTitle).assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertDoesNotExist()
        }
    }

    @Test
    fun backButtonClickInViewMode_shouldPopBackToRecordsScreen() {
        val targetTitle = "Record For View Back Test"
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            loginDataRepository.upsertLoginData(
                LoginData(
                    id = 704,
                    title = targetTitle,
                    url = null,
                    userId = "viewback@test.com",
                    password = "secret",
                    notes = null,
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(targetTitle).performClick()

            val backDesc = context.getString(R.string.cd_back_button)
            composeTestRule.onNodeWithContentDescription(backDesc).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(targetTitle)).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(targetTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.search_bar_placeholder))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertDoesNotExist()
        }
    }

    /**
     * Seeds an unlocked vault holding a single authenticator record.
     *
     * @param id Primary key of the record, unique per test so a leaked row cannot mask a failure.
     * @param title Record title the test drives its assertions from.
     */
    private fun seedUnlockedStateWithAuthenticator(id: Int, title: String) {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            authenticatorDataRepository.upsertAuthenticatorData(
                AuthenticatorData(
                    id = id,
                    title = title,
                    config = TotpConfig(secretKey = E2ETestUtils.TEST_TOTP_SECRET_KEY),
                    creationDate = Date(),
                    updateDate = Date()
                )
            )
        }
    }

    @Test
    fun openAuthenticatorRecordInViewMode_shouldShowOneTimeCodeAndNeverExposeSecretKey() {
        val targetTitle = "Authenticator For View Test"
        seedUnlockedStateWithAuthenticator(id = 721, title = targetTitle)

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            E2ETestUtils.waitForRecordTitle(composeTestRule, targetTitle)
            E2ETestUtils.clickRecordRowByTitle(composeTestRule, targetTitle)

            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.totp_code))
            composeTestRule.onNodeWithText(context.getString(R.string.totp_code))
                .assertIsDisplayed()
            composeTestRule.onNode(E2ETestUtils.hasLiveTotpCode()).assertIsDisplayed()

            // the seed is stored on the code field but must only ever leave it as a derived code,
            // and the secret key field itself is create-only.
            composeTestRule.onNode(hasText(E2ETestUtils.TEST_TOTP_SECRET_KEY, substring = true))
                .assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.secret_key))
                .assertDoesNotExist()
        }
    }

    @Test
    fun editExistingAuthenticatorRecord_shouldSaveUpdatedTitleAndReflectInRecordsList() {
        val initialTitle = "Original Authenticator Record"
        val updatedTitle = "Updated Authenticator Record"
        seedUnlockedStateWithAuthenticator(id = 722, title = initialTitle)

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            E2ETestUtils.waitForRecordTitle(composeTestRule, initialTitle)
            E2ETestUtils.clickRecordRowByTitle(composeTestRule, initialTitle)

            val editDesc = context.getString(R.string.cd_action_edit)
            composeTestRule.waitUntilNodeDisplayed(matcher = hasContentDescription(editDesc))
            composeTestRule.onNodeWithContentDescription(editDesc).performClick()

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            ).performTextReplacement(updatedTitle)

            val saveText = context.getString(R.string.save)
            composeTestRule.onNodeWithText(saveText).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(hasText(saveText)).fetchSemanticsNodes().isEmpty()
            }

            composeTestRule.onNodeWithText(updatedTitle).assertIsDisplayed()
            composeTestRule.onNodeWithText(initialTitle).assertDoesNotExist()
            // renaming must not disturb the stored config, so the live badge has to survive.
            composeTestRule.onNodeWithContentDescription(
                context.getString(R.string.cd_copy_totp_code),
                useUnmergedTree = true
            ).assertIsDisplayed()
        }
    }

    @Test
    fun deleteExistingAuthenticatorRecord_shouldRemoveRecordFromList() {
        val targetTitle = "Authenticator Record To Delete"
        seedUnlockedStateWithAuthenticator(id = 723, title = targetTitle)

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) {
            E2ETestUtils.waitForRecordTitle(composeTestRule, targetTitle)
            E2ETestUtils.clickRecordRowByTitle(composeTestRule, targetTitle)

            val deleteDesc = context.getString(R.string.cd_action_delete)
            composeTestRule.waitUntilNodeDisplayed(matcher = hasContentDescription(deleteDesc))
            composeTestRule.onNodeWithContentDescription(deleteDesc).performClick()

            composeTestRule.onNodeWithText(context.getString(R.string.delete_this_record))
                .assertIsDisplayed()
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
    fun clickCopyOnAuthenticatorRowBadge_shouldCopyDerivedCodeAndNotTheStoredSeed() {
        val targetTitle = "Authenticator Record To Copy"
        seedUnlockedStateWithAuthenticator(id = 724, title = targetTitle)

        E2ETestUtils.launchUnlockedScenario(composeTestRule, context) { scenario ->
            E2ETestUtils.waitForRecordTitle(composeTestRule, targetTitle)

            composeTestRule.onNodeWithContentDescription(
                context.getString(R.string.cd_copy_totp_code),
                useUnmergedTree = true
            ).onParent().performClick()
            composeTestRule.waitForIdle()

            // Tiramisu and above suppress the in-app confirmation in favour of the system
            // clipboard overlay, so the clipboard itself is the only observable outcome.
            var copiedText: String? = null
            scenario.onActivity { activity ->
                val clipboardManager = activity.getSystemService(ClipboardManager::class.java)
                copiedText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
            }

            // sharing the seed would hand over the second factor permanently, so only the code
            // valid right now may reach the clipboard.
            assertThat(copiedText).matches("\\d{6}")
            composeTestRule.onNodeWithText(context.getString(R.string.search_bar_placeholder))
                .assertIsDisplayed()
        }
    }
}
