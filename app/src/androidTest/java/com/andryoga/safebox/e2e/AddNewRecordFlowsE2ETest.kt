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
            E2ETestUtils.resetAppState(
                safeBoxDatabase = safeBoxDatabase,
                settingsDataStore = settingsDataStore,
                activeSessionManager = activeSessionManager
            )
        }
    }

    private fun createRecordAndAssert(
        typeResId: Int,
        title: String,
        extraFields: List<Pair<Int, String>> = emptyList()
    ) {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.clickAddNewRecordOption(
                composeTestRule,
                context,
                typeResId
            )

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            ).performTextInput(title)

            extraFields.forEach { (labelResId, value) ->
                composeTestRule.onNode(
                    hasSetTextAction() and hasText(
                        context.getString(labelResId),
                        substring = true
                    )
                ).performTextInput(value)
            }

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                composeTestRule.onAllNodes(
                    hasText(title, substring = true),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun addNewLoginRecord_shouldSaveAndAppearInRecordsList() {
        createRecordAndAssert(
            typeResId = R.string.type_display_login,
            title = "E2E Login Title",
            extraFields = listOf(
                R.string.user_id to "user@test.com",
                R.string.password to "SecretPass!123"
            )
        )
    }

    @Test
    fun addNewNoteRecord_shouldSaveAndAppearInRecordsList() {
        createRecordAndAssert(
            typeResId = R.string.type_display_note,
            title = "E2E Note Title",
            extraFields = listOf(
                R.string.notes to "Very secret notes content for testing"
            )
        )
    }

    @Test
    fun addNewBankCardRecord_shouldSaveAndAppearInRecordsList() {
        createRecordAndAssert(
            typeResId = R.string.type_display_card,
            title = "E2E Card Title",
            extraFields = listOf(
                R.string.number to "4111111111111111"
            )
        )
    }

    @Test
    fun addNewBankAccountRecord_shouldSaveAndAppearInRecordsList() {
        createRecordAndAssert(
            typeResId = R.string.type_display_account,
            title = "E2E Bank Account Title",
            extraFields = listOf(
                R.string.account_number to "0987654321"
            )
        )
    }
}
