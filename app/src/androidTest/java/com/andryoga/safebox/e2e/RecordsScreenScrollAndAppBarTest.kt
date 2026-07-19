@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.sampleData.RandomUserData
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.mappers.record.toRecordListItem
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * End-to-End (E2E) Hilt UI Test suite for [RecordsScreen] scrolling behavior and Top App Bar collapsing/visibility.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecordsScreenScrollAndAppBarTest {

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
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

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
    fun emptyRecordsState_topAppBarShouldBeVisibleAndShowAddNewButton() {
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

            // Verify empty screen central elements
            composeTestRule.onNodeWithText(context.getString(R.string.no_record))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertIsDisplayed()

            // Verify Top App Bar is visible and contains the Add New (`+`) action button
            val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
            composeTestRule.onNodeWithContentDescription(addNewButtonDesc).assertIsDisplayed()
        }
    }

    @Test
    fun scrollLongPopulatedList_shouldKeepListAccessibleAndCheckTopBar() {
        lateinit var firstRecordTitle: String
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            // Seed 200 records using RandomUserData
            RandomUserData.insertRandomData(
                loginDataRepository,
                bankAccountDataRepository,
                bankCardDataRepository,
                secureNoteDataRepository
            )

            val loginData =
                loginDataRepository.getAllLoginData().first().map { it.toRecordListItem() }
            val bankAccountData = bankAccountDataRepository.getAllBankAccountData().first()
                .map { it.toRecordListItem() }
            val cardData =
                bankCardDataRepository.getAllBankCardData().first().map { it.toRecordListItem() }
            val noteData = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { it.toRecordListItem() }
            val combinedSorted =
                (loginData + bankAccountData + cardData + noteData).sortedBy { it.title.lowercase() }
            firstRecordTitle = combinedSorted.first().title
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            // Verify Top App Bar is initially visible
            val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
            composeTestRule.onNodeWithContentDescription(addNewButtonDesc).assertIsDisplayed()

            composeTestRule.waitUntil(timeoutMillis = 20000L) {
                composeTestRule.onAllNodes(hasScrollToIndexAction(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                        composeTestRule.onAllNodes(hasText(firstRecordTitle, substring = true))
                            .fetchSemanticsNodes().isNotEmpty()
            }

            // Scroll down the LazyColumn to index 40 and verify items and Top App Bar remain accessible
            composeTestRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
                .performScrollToIndex(40)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription(addNewButtonDesc).assertIsDisplayed()
        }
    }

    @Test
    fun scrollLongPopulatedListToEnd_lastRecordShouldBeCompletelyVisibleAndUnobstructed() {
        lateinit var firstRecordTitle: String
        lateinit var lastRecordTitle: String
        var combinedSortedSize = 0
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )

            RandomUserData.insertRandomData(
                loginDataRepository,
                bankAccountDataRepository,
                bankCardDataRepository,
                secureNoteDataRepository
            )

            val loginData =
                loginDataRepository.getAllLoginData().first().map { it.toRecordListItem() }
            val bankAccountData = bankAccountDataRepository.getAllBankAccountData().first()
                .map { it.toRecordListItem() }
            val cardData =
                bankCardDataRepository.getAllBankCardData().first().map { it.toRecordListItem() }
            val noteData = secureNoteDataRepository.getAllSecureNoteData().first()
                .map { it.toRecordListItem() }
            val combinedSorted =
                (loginData + bankAccountData + cardData + noteData).sortedBy { it.title.lowercase() }
            firstRecordTitle = combinedSorted.first().title
            lastRecordTitle = combinedSorted.last().title
            combinedSortedSize = combinedSorted.size
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            composeTestRule.waitUntil(timeoutMillis = 20000L) {
                composeTestRule.onAllNodes(hasScrollToIndexAction(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                        composeTestRule.onAllNodes(hasText(firstRecordTitle, substring = true))
                            .fetchSemanticsNodes().isNotEmpty()
            }

            val headerItemCount = 1
            composeTestRule.onNode(hasScrollToIndexAction(), useUnmergedTree = true)
                .performScrollToIndex(combinedSortedSize + headerItemCount)
            composeTestRule.waitUntil(timeoutMillis = 20000L) {
                composeTestRule.onAllNodes(
                    hasText(
                        lastRecordTitle,
                        substring = true
                    ) and androidx.compose.ui.test.hasClickAction(),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(
                hasText(
                    lastRecordTitle,
                    substring = true
                ) and androidx.compose.ui.test.hasClickAction(),
                useUnmergedTree = true
            ).assertIsDisplayed()
        }
    }
}
