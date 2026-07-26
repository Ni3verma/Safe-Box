@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.domain.models.record.BankAccountData
import com.andryoga.safebox.domain.models.record.CardData
import com.andryoga.safebox.domain.models.record.LoginData
import com.andryoga.safebox.domain.models.record.NoteData
import com.andryoga.safebox.e2e.E2ETestUtils.TEST_MASTER_PASSWORD
import com.andryoga.safebox.e2e.E2ETestUtils.unlockApp
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
import java.util.Date

/**
 * Common, non-duplicated utility methods for SafeBox End-to-End (E2E) Hilt integration tests.
 * Manages database setup, authentication unlock flows, and bottom sheet navigation triggers.
 */
object E2ETestUtils {

    const val TEST_MASTER_PASSWORD = "Qwerty@@123"
    const val TEST_MASTER_HINT = "E2E Master Hint"
    val TEST_DATE: Date = Date(1700000000000L)

    /**
     * Pre-seeds the database and encrypted preferences so the app boots directly onto the LoginScreen.
     */
    suspend fun setupUnlockedHomeState(
        safeBoxDatabase: SafeBoxDatabase,
        userDetailsRepository: UserDetailsRepository,
        encryptedPreferenceProvider: EncryptedPreferenceProvider,
        preferenceProvider: PreferenceProvider,
        settingsDataStore: SettingsDataStore? = null
    ) {
        safeBoxDatabase.clearAllTables()
        userDetailsRepository.insertUserDetailsData(TEST_MASTER_PASSWORD, TEST_MASTER_HINT)
        encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, false)
        preferenceProvider.upsertIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, 0)
        settingsDataStore?.let {
            setupSettingsState(
                settingsDataStore = it,
                isPrivacyEnabled = SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT,
                awayTimeoutSec = SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT,
                autoBackupAfterLogin = SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT,
                passwordAfterXBiometrics = SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
            )
        }
    }

    /**
     * Pre-seeds the database and preferences so the app boots requiring signup.
     */
    suspend fun setupSignupRequiredState(
        safeBoxDatabase: SafeBoxDatabase,
        encryptedPreferenceProvider: EncryptedPreferenceProvider,
        settingsDataStore: SettingsDataStore? = null
    ) {
        safeBoxDatabase.clearAllTables()
        encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
        settingsDataStore?.let {
            setupSettingsState(
                settingsDataStore = it,
                isPrivacyEnabled = SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT,
                awayTimeoutSec = SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT,
                autoBackupAfterLogin = SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT,
                passwordAfterXBiometrics = SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT
            )
        }
    }

    /**
     * Pre-seeds credentials with biometric login count enabled (`remainingCount` > 0).
     */
    suspend fun setupBiometricAvailableState(
        safeBoxDatabase: SafeBoxDatabase,
        userDetailsRepository: UserDetailsRepository,
        encryptedPreferenceProvider: EncryptedPreferenceProvider,
        preferenceProvider: PreferenceProvider,
        remainingCount: Int = 3,
        settingsDataStore: SettingsDataStore? = null
    ) {
        safeBoxDatabase.clearAllTables()
        userDetailsRepository.insertUserDetailsData(TEST_MASTER_PASSWORD, TEST_MASTER_HINT)
        encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, false)
        preferenceProvider.upsertIntPref(
            CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING,
            remainingCount
        )
        settingsDataStore?.let {
            setupSettingsState(
                settingsDataStore = it,
                isPrivacyEnabled = SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT,
                awayTimeoutSec = SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT,
                autoBackupAfterLogin = SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT,
                passwordAfterXBiometrics = remainingCount
            )
        }
    }

    /**
     * Pre-seeds the database with comprehensive sample records across all 4 vault item types
     * to guarantee deterministic testing for search, filter, and detail screens without UI interaction.
     */
    suspend fun setupSeededVaultRecords(
        safeBoxDatabase: SafeBoxDatabase,
        loginDataRepository: LoginDataRepository,
        bankCardDataRepository: BankCardDataRepository,
        bankAccountDataRepository: BankAccountDataRepository,
        secureNoteDataRepository: SecureNoteDataRepository
    ) {
        safeBoxDatabase.loginDataDao().deleteAllData()
        safeBoxDatabase.bankCardDataDao().deleteAllData()
        safeBoxDatabase.bankAccountDataDao().deleteAllData()
        safeBoxDatabase.secureNoteDataDao().deleteAllData()
        loginDataRepository.upsertLoginData(
            LoginData(
                id = 901,
                title = "Apple ID Login",
                url = "apple.com",
                userId = "user@apple.com",
                password = "ApplePassword123!",
                notes = "2FA enabled",
                creationDate = TEST_DATE,
                updateDate = TEST_DATE
            )
        )
        bankCardDataRepository.upsertBankCardData(
            CardData(
                id = 902,
                title = "Chase Sapphire Card",
                name = "John Doe",
                number = "4111222233334444",
                expiryDate = "1228",
                cvv = "999",
                pin = "1234",
                notes = "Travel points card",
                creationDate = TEST_DATE,
                updateDate = TEST_DATE
            )
        )
        bankAccountDataRepository.upsertBankAccountData(
            BankAccountData(
                id = 903,
                title = "Silicon Valley Checking",
                accountNo = "9876543210",
                customerName = "John Doe",
                customerId = "CUST-999",
                branchCode = "001",
                branchName = "Main Branch",
                branchAddress = "123 Tech Way",
                ifscCode = "SVFB0001234",
                micrCode = "123456789",
                notes = "Primary checking",
                creationDate = TEST_DATE,
                updateDate = TEST_DATE
            )
        )
        secureNoteDataRepository.upsertSecureNoteData(
            NoteData(
                id = 904,
                title = "Wifi Router Secrets",
                notes = "SSID: SafeBox_5G\nPassword: SecureWifiPassword#2026\nAdmin IP: 192.168.1.1",
                creationDate = TEST_DATE,
                updateDate = TEST_DATE
            )
        )
    }

    /**
     * Pre-seeds backup metadata to simulate an active configured backup directory and timestamp.
     */
    suspend fun setupBackupMetadataState(
        backupMetadataRepository: BackupMetadataRepository,
        mockUriString: String = "file:///sdcard/SafeboxBackups",
        mockTimestamp: Long = TEST_DATE.time
    ) {
        backupMetadataRepository.insertBackupMetadata(Uri.parse(mockUriString))
        backupMetadataRepository.updateLastBackupDate(mockTimestamp)
    }

    /**
     * Pre-seeds SettingsDataStore settings properties before ActivityScenario launch.
     */
    suspend fun setupSettingsState(
        settingsDataStore: SettingsDataStore,
        isPrivacyEnabled: Boolean = true,
        awayTimeoutSec: Int = 10,
        autoBackupAfterLogin: Boolean = false,
        passwordAfterXBiometrics: Int = 5
    ) {
        settingsDataStore.updatePrivacy(isPrivacyEnabled)
        settingsDataStore.updateAwayTimeout(awayTimeoutSec)
        settingsDataStore.updateAutoBackupAfterPasswordLogin(autoBackupAfterLogin)
        settingsDataStore.updatePasswordAfterXBiometricLogin(passwordAfterXBiometrics)
    }

    /**
     * Resets settings, active session manager state, and database tables for test teardowns.
     */
    suspend fun resetAppState(
        safeBoxDatabase: SafeBoxDatabase,
        settingsDataStore: SettingsDataStore,
        activeSessionManager: ActiveSessionManager,
        encryptedPreferenceProvider: EncryptedPreferenceProvider? = null,
        isSignUpRequired: Boolean = false
    ) {
        settingsDataStore.updateAwayTimeout(SettingsDataStore.DefaultValues.AWAY_TIMEOUT_DEFAULT)
        settingsDataStore.updatePrivacy(SettingsDataStore.DefaultValues.PRIVACY_ENABLED_DEFAULT)
        settingsDataStore.updateAutoBackupAfterPasswordLogin(SettingsDataStore.DefaultValues.AUTO_BACKUP_AFTER_PASSWORD_LOGIN_DEFAULT)
        settingsDataStore.updatePasswordAfterXBiometricLogin(SettingsDataStore.DefaultValues.PASSWORD_AFTER_X_BIOMETRIC_LOGIN_DEFAULT)
        activeSessionManager.setPaused(true)
        safeBoxDatabase.clearAllTables()
        encryptedPreferenceProvider?.upsertBooleanPref(
            CommonConstants.IS_SIGN_UP_REQUIRED,
            isSignUpRequired
        )
    }

    /**
     * Creates a stable [LifecycleOwner] for testing session timeout triggers.
     */
    fun createTestLifecycleOwner(): LifecycleOwner {
        return object : LifecycleOwner {
            private val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle
                get() = registry
        }
    }

    /**
     * Closes the soft keyboard if active and waits for layout insets to settle.
     */
    fun closeSoftKeyboard(composeTestRule: ComposeTestRule, context: Context) {
        var activity: Activity?
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = activity?.currentFocus?.windowToken
            if (imm != null && token != null) {
                imm.hideSoftInputFromWindow(token, 0)
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Launches [MainActivity], executes [unlockApp] if needed, and scopes the [ActivityScenario] lifecycle.
     */
    inline fun launchUnlockedScenario(
        composeTestRule: ComposeTestRule,
        context: Context,
        crossinline block: (ActivityScenario<MainActivity>) -> Unit
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            unlockApp(composeTestRule, context)
            block(scenario)
        }
    }

    /**
     * Dismisses any active timeout dialog, verifies the [LoginScreen] or unlocked [RecordsScreen],
     * inputs the [TEST_MASTER_PASSWORD], and waits for the Home screen to render.
     */
    fun unlockApp(composeTestRule: ComposeTestRule, context: Context) {
        val welcomeBackText = context.getString(R.string.welcome_back)
        val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.waitUntil(timeoutMillis = 25000L) {
            runCatching {
                val timeoutNodes = composeTestRule.onAllNodes(
                    hasText(context.getString(R.string.timeout_dialog_message)),
                    useUnmergedTree = true
                ).fetchSemanticsNodes()
                if (timeoutNodes.isNotEmpty()) {
                    val btnNodes = composeTestRule.onAllNodes(
                        hasText(context.getString(R.string.timeout_dialog_positive_button_text)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes()
                    if (btnNodes.isNotEmpty()) {
                        composeTestRule.onAllNodes(
                            hasText(context.getString(R.string.timeout_dialog_positive_button_text)),
                            useUnmergedTree = true
                        ).onFirst().performClick()
                    }
                }
                val isWelcome = composeTestRule.onAllNodes(
                    hasText(welcomeBackText),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
                val isHome = composeTestRule.onAllNodes(
                    hasContentDescription(addNewButtonDesc),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
                isWelcome || isHome
            }.getOrDefault(false)
        }

        val isAlreadyHome = runCatching {
            composeTestRule.onAllNodes(
                hasContentDescription(addNewButtonDesc),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

        if (isAlreadyHome) {
            composeTestRule.waitForIdle()
            return
        }

        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasText(welcomeBackText),
            timeoutMillis = 25000L
        )
        composeTestRule.onAllNodes(
            hasText(welcomeBackText),
            useUnmergedTree = true
        ).onFirst().assertIsDisplayed()
        composeTestRule.waitForIdle()
        val passwordNodes = composeTestRule.onAllNodes(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
        passwordNodes.onFirst().performTextReplacement(TEST_MASTER_PASSWORD)
        composeTestRule.waitForIdle()
        closeSoftKeyboard(composeTestRule, context)
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasContentDescription(addNewButtonDesc),
            timeoutMillis = 25000L
        )
        composeTestRule.waitForIdle()
    }

    /**
     * Opens the [AddNewRecordBottomSheet] via the top app bar `+` button and clicks the specified record option.
     */
    fun clickAddNewRecordOption(
        composeTestRule: ComposeTestRule,
        context: Context,
        optionResId: Int
    ) {
        val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasContentDescription(addNewButtonDesc),
            timeoutMillis = 25000L
        )
        composeTestRule.onAllNodes(
            hasContentDescription(addNewButtonDesc),
            useUnmergedTree = true
        ).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(
            hasContentDescription(addNewButtonDesc),
            useUnmergedTree = true
        ).onFirst().performClick()

        val optionText = context.getString(optionResId)
        val bottomSheetTitle = context.getString(R.string.add_a_new_record)
        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasText(bottomSheetTitle),
            timeoutMillis = 25000L
        )
        composeTestRule.onAllNodes(
            hasText(optionText),
            useUnmergedTree = true
        ).onLast().assertIsDisplayed()
        composeTestRule.onAllNodes(
            hasText(optionText),
            useUnmergedTree = true
        ).onLast().performClick()
    }

    /**
     * Unlocks the app via [unlockApp] and waits until a specific record title appears on the Home [RecordsScreen].
     */
    fun unlockAppAndWaitForTitle(
        composeTestRule: ComposeTestRule,
        context: Context,
        title: String,
        timeoutMillis: Long = 25000L
    ) {
        unlockApp(composeTestRule, context)
        waitForRecordTitle(composeTestRule, title, timeoutMillis)
    }

    /**
     * Unlocks the app via [unlockApp] and optionally waits until an expected title appears on the Home [RecordsScreen].
     */
    fun unlockAppAndWaitForRecord(
        composeTestRule: ComposeTestRule,
        context: Context,
        expectedTitle: String? = null,
        timeoutMillis: Long = 25000L
    ) {
        unlockApp(composeTestRule, context)
        if (expectedTitle != null) {
            waitForRecordTitle(composeTestRule, expectedTitle, timeoutMillis)
        }
    }

    /**
     * Waits until at least one semantics node matching [title] is present in the semantics tree.
     */
    fun waitForRecordTitle(
        composeTestRule: ComposeTestRule,
        title: String,
        timeoutMillis: Long = 25000L
    ) {
        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasText(title, substring = true),
            timeoutMillis = timeoutMillis
        )
    }

    /**
     * Waits until at least one semantics node matching [text] is displayed in the semantics tree.
     */
    fun waitForText(
        composeTestRule: ComposeTestRule,
        text: String,
        timeoutMillis: Long = 25000L
    ) {
        composeTestRule.waitUntilNodeDisplayed(
            matcher = hasText(text),
            timeoutMillis = timeoutMillis
        )
    }
}

/**
 * Extension function on [ComposeTestRule] to wait until at least one node matching [matcher] is displayed in the semantics tree.
 */
fun ComposeTestRule.waitUntilNodeDisplayed(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = 25000L,
    assertDisplayed: Boolean = false
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        runCatching {
            val nodes = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            nodes.isNotEmpty() && (!assertDisplayed || runCatching {
                onAllNodes(
                    matcher,
                    useUnmergedTree = true
                ).onFirst().assertIsDisplayed(); true
            }.getOrDefault(false))
        }.getOrDefault(false)
    }
}

