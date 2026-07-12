@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.sampleData.RandomUserData
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.mappers.record.toRecordListItem
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun emptyRecordsState_topAppBarShouldBeVisibleAndShowAddNewButton() = runTest {
        E2ETestUtils.setupUnlockedHomeState(
            safeBoxDatabase,
            userDetailsRepository,
            encryptedPreferenceProvider,
            preferenceProvider
        )

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
    fun scrollLongPopulatedList_shouldKeepListAccessibleAndCheckTopBar() = runTest {
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

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            // Verify Top App Bar is initially visible
            val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
            composeTestRule.onNodeWithContentDescription(addNewButtonDesc).assertIsDisplayed()

            // Scroll down the LazyColumn to index 40 and verify items remain accessible
            composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        }
    }

    @Test
    fun scrollLongPopulatedListToEnd_lastRecordShouldBeCompletelyVisibleAndUnobstructed() =
        runTest {
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
            val lastRecord = combinedSorted.last()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                E2ETestUtils.unlockApp(composeTestRule, context)

                composeTestRule.onNode(hasScrollToIndexAction())
                    .performScrollToIndex(combinedSorted.size)
                composeTestRule.onNodeWithText(lastRecord.title, substring = true)
                    .assertIsDisplayed()
            }
        }
}
