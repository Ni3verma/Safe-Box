@file:Suppress("DEPRECATION")

package com.andryoga.safebox.e2e

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import java.util.Date

/**
 * Common, non-duplicated utility methods for SafeBox End-to-End (E2E) Hilt integration tests.
 * Manages database setup, authentication unlock flows, and bottom sheet navigation triggers.
 */
object E2ETestUtils {

    const val TEST_MASTER_PASSWORD = "Qwerty@@123"
    const val TEST_MASTER_HINT = "E2E Master Hint"

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
                creationDate = Date(),
                updateDate = Date()
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
                creationDate = Date(),
                updateDate = Date()
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
                creationDate = Date(),
                updateDate = Date()
            )
        )
        secureNoteDataRepository.upsertSecureNoteData(
            NoteData(
                id = 904,
                title = "Wifi Router Secrets",
                notes = "SSID: SafeBox_5G\nPassword: SecureWifiPassword#2026\nAdmin IP: 192.168.1.1",
                creationDate = Date(),
                updateDate = Date()
            )
        )
    }

    /**
     * Pre-seeds backup metadata to simulate an active configured backup directory and timestamp.
     */
    suspend fun setupBackupMetadataState(
        backupMetadataRepository: BackupMetadataRepository,
        mockUriString: String = "content://com.android.externalstorage.documents/tree/primary%3ASafeboxBackups",
        mockTimestamp: Long = System.currentTimeMillis()
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
     * Unlocks the app from [LoginScreen] and confirms transition to the Home [RecordsScreen].
     */
    fun unlockApp(composeTestRule: ComposeTestRule, context: Context) {
        composeTestRule.waitForIdle()
        runCatching {
            val timeoutText = context.getString(R.string.timeout_dialog_message)
            val positiveBtnText = context.getString(R.string.timeout_dialog_positive_button_text)
            val timeoutNodes = composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText(timeoutText),
                useUnmergedTree = true
            ).fetchSemanticsNodes()
            if (timeoutNodes.isNotEmpty()) {
                val btnNodes = composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasText(positiveBtnText),
                    useUnmergedTree = true
                ).fetchSemanticsNodes()
                if (btnNodes.isNotEmpty()) {
                    composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(positiveBtnText),
                        useUnmergedTree = true
                    ).onFirst().performClick()
                    composeTestRule.waitForIdle()
                }
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 15000L) {
            runCatching {
                val timeoutNodes = composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasText(context.getString(R.string.timeout_dialog_message)),
                    useUnmergedTree = true
                ).fetchSemanticsNodes()
                if (timeoutNodes.isNotEmpty()) {
                    val btnNodes = composeTestRule.onAllNodes(
                        androidx.compose.ui.test.hasText(context.getString(R.string.timeout_dialog_positive_button_text)),
                        useUnmergedTree = true
                    ).fetchSemanticsNodes()
                    if (btnNodes.isNotEmpty()) {
                        composeTestRule.onAllNodes(
                            androidx.compose.ui.test.hasText(context.getString(R.string.timeout_dialog_positive_button_text)),
                            useUnmergedTree = true
                        ).onFirst().performClick()
                    }
                }
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasText(context.getString(R.string.welcome_back)),
            useUnmergedTree = true
        ).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        ).onFirst()
            .performTextReplacement(TEST_MASTER_PASSWORD)
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasText(context.getString(R.string.login)),
            useUnmergedTree = true
        ).onFirst().performClick()
        val addNewButtonDesc = context.getString(R.string.cd_add_new_record_button)
        composeTestRule.waitUntil(timeoutMillis = 15000L) {
            runCatching {
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription(addNewButtonDesc),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
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
        composeTestRule.waitUntil(timeoutMillis = 15000L) {
            runCatching {
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription(
                        addNewButtonDesc
                    ),
                    useUnmergedTree = true
                )
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasContentDescription(addNewButtonDesc),
            useUnmergedTree = true
        ).onFirst().assertIsDisplayed()
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasContentDescription(addNewButtonDesc),
            useUnmergedTree = true
        ).onFirst().performClick()

        val optionText = context.getString(optionResId)
        val bottomSheetTitle = context.getString(R.string.add_a_new_record)
        composeTestRule.waitUntil(timeoutMillis = 15000L) {
            runCatching {
                composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasText(bottomSheetTitle),
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasText(optionText),
            useUnmergedTree = true
        ).onLast().assertIsDisplayed()
        composeTestRule.onAllNodes(
            androidx.compose.ui.test.hasText(optionText),
            useUnmergedTree = true
        ).onLast().performClick()
    }
}
