package com.andryoga.safebox.e2e

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
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
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
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
 * The vault's decrypted content never outlives the session: with a real record open, a lock or a
 * process-death restore must end on Login with none of the record's plaintext in the tree, and back
 * must not bring it back. The empty-vault variants of the same gate live in
 * [HomeScreenNavigationAndTimeoutE2ETest].
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BypassProtectionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var activeSessionManager: ActiveSessionManager

    @Inject
    lateinit var loginDataRepository: LoginDataRepository

    @Inject
    lateinit var bankCardDataRepository: BankCardDataRepository

    @Inject
    lateinit var bankAccountDataRepository: BankAccountDataRepository

    @Inject
    lateinit var secureNoteDataRepository: SecureNoteDataRepository

    @Inject
    lateinit var authenticatorDataRepository: AuthenticatorDataRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(safeBoxDatabase, userDetailsRepository, preferenceProvider)
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository,
            )
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            E2ETestUtils.resetAppState(safeBoxDatabase, settingsDataStore, activeSessionManager)
        }
    }

    @Test
    fun lockVault_whileARecordIsOpen_shouldLeaveForLoginAndKeepItsPlaintextOffScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openLoginRecord()

            VaultSessionTestBridge.lockVault()

            assertOnLoginWithoutPlaintext()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasText(LOGIN_USER_ID, substring = true)).assertDoesNotExist()
        }
    }

    /** Process death restores the nested back stack onto the record while the RAM-only key is gone. */
    @Test
    fun recreateIntoAnOpenRecord_whenTheVaultIsLocked_shouldOpenLoginWithoutComposingTheRecord() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openLoginRecord()

            VaultSessionTestBridge.lockVault()
            scenario.recreate()

            assertOnLoginWithoutPlaintext()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasText(LOGIN_USER_ID, substring = true)).assertDoesNotExist()
        }
    }

    private fun openLoginRecord() {
        E2ETestUtils.unlockAppAndWaitForTitle(composeTestRule, context, LOGIN_TITLE)
        E2ETestUtils.clickRecordRowByTitle(composeTestRule, LOGIN_TITLE)
        // The decrypted user id on screen proves the record view is up before the lock.
        composeTestRule.waitUntilNodeDisplayed(hasText(LOGIN_USER_ID, substring = true))
    }

    private fun assertOnLoginWithoutPlaintext() {
        composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
        composeTestRule.onNode(hasText(LOGIN_USER_ID, substring = true)).assertDoesNotExist()
        composeTestRule.onNodeWithText(LOGIN_TITLE).assertDoesNotExist()
    }

    private companion object {
        const val LOGIN_TITLE = "Apple ID Login"
        const val LOGIN_USER_ID = "user@apple.com"
    }
}
