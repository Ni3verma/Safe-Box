package com.andryoga.safebox.e2e

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.AndroidVaultStorageStats
import com.andryoga.safebox.common.VaultStorageStats
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.di.FakeDeviceSecurityAuthProvider
import com.andryoga.safebox.di.TelemetryModule
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeDeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
import com.andryoga.safebox.ui.core.TestTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The envelope vault's lifecycle journeys end to end on a `crypto_version = 2` vault: forgot
 * password through the device credential, erase when recovery was never set up, change password
 * from Settings, and the fingerprint switch. Each ends by proving the stored state, not just the
 * screen, because a journey can look right while leaving the wrong wrap behind.
 *
 * The device reports a secured lock screen and a strong biometric ([FakeDeviceSecurityStatus], this
 * class only); the prompt is [FakeDeviceSecurityAuthProvider] and the keys are the test gateway's.
 */
@HiltAndroidTest
@UninstallModules(TelemetryModule::class)
@RunWith(AndroidJUnit4::class)
class VaultLifecycleE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val fakeDeviceSecurityStatus = FakeDeviceSecurityStatus()

    @BindValue
    @JvmField
    val deviceSecurityStatus: DeviceSecurityStatus = fakeDeviceSecurityStatus

    @BindValue
    @JvmField
    val vaultStorageStats: VaultStorageStats = AndroidVaultStorageStats(context)

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
    lateinit var keystoreGateway: FakeKeystoreGateway

    @Inject
    lateinit var fakeDeviceSecurityAuthProvider: FakeDeviceSecurityAuthProvider

    @Before
    fun setup() {
        hiltRule.inject()
        keystoreGateway.reset()
        fakeDeviceSecurityAuthProvider.reset()
        runBlocking {
            // Biometric count 0: the login form shows instead of an automatic fingerprint prompt.
            E2ETestUtils.setupUnlockedHomeState(safeBoxDatabase, userDetailsRepository, preferenceProvider)
        }
    }

    @After
    fun tearDown() {
        keystoreGateway.reset()
        fakeDeviceSecurityAuthProvider.reset()
        runBlocking {
            E2ETestUtils.resetAppState(safeBoxDatabase, settingsDataStore, activeSessionManager)
        }
    }

    @Test
    fun forgotPassword_throughTheDeviceCredential_shouldSetANewPasswordThatOpensTheVault() {
        runBlocking { VaultSessionTestBridge.seedDeviceWrap() }
        VaultSessionTestBridge.lockVault()
        fakeDeviceSecurityAuthProvider.canAuthenticateOverride = true
        fakeDeviceSecurityAuthProvider.authHandlerOverride = { onSuccess, _ -> onSuccess(null) }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.welcome_back))
            composeTestRule.onNodeWithText(context.getString(R.string.forgot_password)).performClick()

            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.update_password))
            fillNewPasswordDialog(NEW_PASSWORD, NEW_HINT)
            awaitHome()
            assertThat(fakeDeviceSecurityAuthProvider.lastCryptoObject?.cipher).isNotNull()

            // Maintenance has now created a biometric wrap; without this the next Login would open by fingerprint.
            fakeDeviceSecurityAuthProvider.reset()
            VaultSessionTestBridge.lockVault()
            assertPasswordRejected(E2ETestUtils.TEST_MASTER_PASSWORD)
            E2ETestUtils.unlockApp(composeTestRule, context, NEW_PASSWORD)
        }
        runBlocking {
            assertThat(userDetailsRepository.getHint()).isEqualTo(NEW_HINT)
            // The DEK was recovered, not replaced: the device wrap still opens the same vault.
            assertThat(safeBoxDatabase.vaultMetaDao().get(VaultMetaKey.WRAPPED_DEK_DEVICE)).isNotNull()
        }
    }

    @Test
    fun forgotPassword_withoutADeviceWrap_eraseShouldEmptyTheVaultAndOpenSignup() {
        // No lock screen at signup time: no device wrap exists, so recovery is not set up.
        fakeDeviceSecurityStatus.isDeviceSecure = false
        VaultSessionTestBridge.lockVault()

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.welcome_back))
            composeTestRule.onNodeWithText(context.getString(R.string.forgot_password)).performClick()

            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.recovery_not_set_up_title))
            composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_action)).performClick()
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.erase_vault_dialog_title))
            composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_confirm)).performClick()

            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.welcome))
        }
        runBlocking {
            assertThat(safeBoxDatabase.vaultMetaDao().get(VaultMetaKey.CRYPTO_VERSION)).isNull()
            assertThat(safeBoxDatabase.userDetailsDao().getUserCount()).isEqualTo(0)
        }
    }

    @Test
    fun changePassword_fromSettingsWithTheCurrentPassword_shouldMakeOnlyTheNewPasswordOpenTheVault() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            openSettings()
            composeTestRule.onNodeWithText(context.getString(R.string.change_master_password))
                .performScrollTo()
                .performClick()

            // No device prompt is available, so the gate asks for the current password.
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.settings_current_password_title))
            composeTestRule.onNode(
                hasSetTextAction() and hasText(context.getString(R.string.settings_current_password_label)),
            ).performTextInput(E2ETestUtils.TEST_MASTER_PASSWORD)
            composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()

            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.update_password))
            fillNewPasswordDialog(NEW_PASSWORD, NEW_HINT)
            // The re-wrap commits after the dialog closes and a lock cancels it, so wait for the
            // commit itself (the hint rides in the same transaction) before locking.
            composeTestRule.waitUntil(timeoutMillis = 15000L) {
                runBlocking { userDetailsRepository.getHint() } == NEW_HINT
            }

            VaultSessionTestBridge.lockVault()
            assertPasswordRejected(E2ETestUtils.TEST_MASTER_PASSWORD)
            E2ETestUtils.unlockApp(composeTestRule, context, NEW_PASSWORD)
        }
        runBlocking { assertThat(userDetailsRepository.getHint()).isEqualTo(NEW_HINT) }
    }

    @Test
    fun fingerprintSwitch_offThenOn_shouldDeleteAndRecreateTheBiometricWrapWithoutAPrompt() {
        runBlocking { VaultSessionTestBridge.seedBiometricWrap() }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            E2ETestUtils.unlockApp(composeTestRule, context)
            openSettings()
            val switch = composeTestRule.onNodeWithTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH)
            switch.performScrollTo().assertIsOn().performClick()
            composeTestRule.waitUntil(timeoutMillis = 10000L) { biometricWrap() == null }
            assertThat(metaValue(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED))
                .isEqualTo(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED_VALUE)
            switch.assertIsOff()

            switch.performClick()
            composeTestRule.waitUntil(timeoutMillis = 10000L) { biometricWrap() != null }
            assertThat(metaValue(VaultMetaKey.BIOMETRIC_UNLOCK_DISABLED)).isNull()
            switch.assertIsOn()
        }
        assertThat(fakeDeviceSecurityAuthProvider.invocationCount).isEqualTo(0)
    }

    private fun openSettings() {
        composeTestRule.onNodeWithText(context.getString(R.string.bottom_nav_settings)).performClick()
        E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.settings_section_security_and_privacy))
    }

    private fun fillNewPasswordDialog(password: String, hint: String) {
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.new_password) + "*"))
            .performTextInput(password)
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.confirm_new_password) + "*"))
            .performTextInput(password)
        composeTestRule.onNode(hasSetTextAction() and hasText(context.getString(R.string.hint) + "*"))
            .performTextReplacement(hint)
        composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    }

    private fun awaitHome() {
        composeTestRule.waitUntilNodeDisplayed(
            hasContentDescription(context.getString(R.string.cd_add_new_record_button)),
        )
    }

    private fun assertPasswordRejected(password: String) {
        composeTestRule.waitUntilNodeDisplayed(hasText(context.getString(R.string.welcome_back)))
        typePasswordAndLogin(composeTestRule, password)
        E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.incorrect_pswrd_message))
    }

    private fun typePasswordAndLogin(rule: ComposeTestRule, password: String) {
        rule.onNode(hasSetTextAction() and hasText(context.getString(R.string.password), substring = true))
            .performTextReplacement(password)
        E2ETestUtils.closeSoftKeyboard(rule, context)
        rule.onNodeWithText(context.getString(R.string.login)).performClick()
    }

    private fun biometricWrap(): String? = metaValue(VaultMetaKey.WRAPPED_DEK_BIOMETRIC)

    private fun metaValue(key: String): String? = runBlocking { safeBoxDatabase.vaultMetaDao().get(key) }

    private companion object {
        const val NEW_PASSWORD = "Changed##456"
        const val NEW_HINT = "changed hint"
    }
}
