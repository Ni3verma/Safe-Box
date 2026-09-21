@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.qrScanner.ScannedTotpHolder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.components.ActivityComponent
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

    /**
     * Seeds an unlocked vault plus the "camera already asked" flag, which is the only state where
     * the scanner shows its in-app rationale instead of the undriveable system permission prompt.
     */
    private fun setupUnlockedStateWithScannerRationale() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider
            )
            E2ETestUtils.setupCameraPermissionAskedState(preferenceProvider)
        }
    }

    @Test
    fun addNewAuthenticatorRecordViaManualKeyEntry_shouldSaveAndShowLiveCodeInRecordsList() {
        setupUnlockedStateWithScannerRationale()
        val title = "E2E Authenticator Title"

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.openAuthenticatorCreateScreenViaManualEntry(composeTestRule, context)

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            ).performTextInput(title)
            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.secret_key),
                    substring = true
                )
            ).performTextInput(E2ETestUtils.TEST_TOTP_SECRET_KEY)

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            E2ETestUtils.waitForRecordTitle(composeTestRule, title)

            composeTestRule.onNodeWithText(title).assertIsDisplayed()
            // the live badge replaces the subtitle, so its copy control proves the row rendered
            // from the persisted config rather than from a plain saved string.
            composeTestRule.onNodeWithContentDescription(
                context.getString(R.string.cd_copy_totp_code),
                useUnmergedTree = true
            ).assertIsDisplayed()
            composeTestRule.onNode(E2ETestUtils.hasLiveTotpCode()).assertIsDisplayed()
        }
    }

    @Test
    fun addNewAuthenticatorRecordWithNonBase32SecretKey_shouldKeepSaveButtonDisabled() {
        setupUnlockedStateWithScannerRationale()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.openAuthenticatorCreateScreenViaManualEntry(composeTestRule, context)

            composeTestRule.onNode(
                hasSetTextAction() and hasText(
                    context.getString(R.string.title),
                    substring = true
                )
            ).performTextInput("Invalid Seed Authenticator")
            val secretKeyMatcher = hasSetTextAction() and hasText(
                context.getString(R.string.secret_key),
                substring = true
            )
            composeTestRule.onNode(secretKeyMatcher).performTextInput("not-a-base32-seed!")
            composeTestRule.waitForIdle()

            // both mandatory fields are filled, so only the Base32 gate can be holding Save back.
            composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()

            composeTestRule.onNode(secretKeyMatcher)
                .performTextReplacement(E2ETestUtils.TEST_TOTP_SECRET_KEY)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsEnabled()
        }
    }

    @Test
    fun backFromAuthenticatorCreateScreen_shouldReturnToRecordsListInsteadOfScanner() {
        setupUnlockedStateWithScannerRationale()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.openAuthenticatorCreateScreenViaManualEntry(composeTestRule, context)

            composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_back_button))
                .performClick()
            E2ETestUtils.waitForText(
                composeTestRule,
                context.getString(R.string.search_bar_placeholder)
            )

            // the scanner is popped on the way to the create screen, so back must not re-open a
            // camera on a code the user already dealt with.
            composeTestRule.onNodeWithText(context.getString(R.string.qr_scanner_title))
                .assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun closeQrScanner_shouldReturnToRecordsListWithoutCreatingRecord() {
        setupUnlockedStateWithScannerRationale()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            E2ETestUtils.openQrScannerAndDismissRationale(composeTestRule, context)

            composeTestRule.onNodeWithContentDescription(context.getString(R.string.close))
                .performClick()
            composeTestRule.waitUntilNodeDisplayed(
                matcher = hasContentDescription(
                    context.getString(R.string.cd_add_new_record_button)
                )
            )

            composeTestRule.onNodeWithText(context.getString(R.string.qr_scanner_title))
                .assertDoesNotExist()
            composeTestRule.onNodeWithText(context.getString(R.string.new_record_button))
                .assertIsDisplayed()
        }
    }

    @Test
    fun authenticatorCreateScreenPrefilledFromScan_shouldEnableSaveWithoutAnyTyping() {
        setupUnlockedStateWithScannerRationale()
        val scannedTitle = "AcmeCorp - alice@example.com"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            E2ETestUtils.unlockApp(composeTestRule, context)

            // the camera cannot be pointed at a real code from an instrumentation test, so the
            // scan is reproduced at its hand-off point: this is the very object the scanner
            // writes, and the create screen consumes it without knowing who filled it.
            scenario.onActivity { activity ->
                EntryPointAccessors.fromActivity(activity, ScannedTotpEntryPoint::class.java)
                    .scannedTotpHolder()
                    .put(
                        ParsedTotpData(
                            title = scannedTitle,
                            config = TotpConfig(secretKey = E2ETestUtils.TEST_TOTP_SECRET_KEY),
                        ),
                    )
            }
            E2ETestUtils.openAuthenticatorCreateScreenViaManualEntry(composeTestRule, context)

            composeTestRule.onNode(
                hasSetTextAction() and hasText(scannedTitle, substring = true)
            ).assertIsDisplayed()
            // nothing has been typed, so this is the whole point: a scan leaves the user with no
            // field left to touch, and Save must be usable rather than merely visible.
            composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsEnabled()

            composeTestRule.onNodeWithText(context.getString(R.string.save)).performClick()
            E2ETestUtils.waitForRecordTitle(composeTestRule, scannedTitle)

            composeTestRule.onNode(E2ETestUtils.hasLiveTotpCode()).assertIsDisplayed()
        }
    }
}

/**
 * Reaches the activity retained [ScannedTotpHolder] so a test can stand in for the QR scanner.
 *
 * [ScannedTotpHolder] is `@ActivityRetainedScoped`, so field injection into the test class would
 * hand back a different instance from the one the create screen reads. Resolving it from the
 * running activity guarantees both sides share the hand-off.
 */
@EntryPoint
@InstallIn(ActivityComponent::class)
interface ScannedTotpEntryPoint {
    fun scannedTotpHolder(): ScannedTotpHolder
}
