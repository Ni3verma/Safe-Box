package com.andryoga.safebox.e2e

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.AndroidVaultStorageStats
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.common.VaultStorageStats
import com.andryoga.safebox.data.dataStore.SettingsDataStore
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.AuthenticatorDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankAccountDataRepository
import com.andryoga.safebox.data.repository.interfaces.BankCardDataRepository
import com.andryoga.safebox.data.repository.interfaces.LoginDataRepository
import com.andryoga.safebox.data.repository.interfaces.SecureNoteDataRepository
import com.andryoga.safebox.di.FakeDeviceSecurityAuthProvider
import com.andryoga.safebox.di.TelemetryModule
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.security.envelope.interfaces.SessionKeyHolder
import com.andryoga.safebox.security.interfaces.DeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeDeviceSecurityStatus
import com.andryoga.safebox.test.fakes.FakeKeystoreGateway
import com.andryoga.safebox.test.fixtures.LegacyVaultFixtures
import com.andryoga.safebox.ui.MainActivity
import com.andryoga.safebox.ui.core.ActiveSessionManager
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
 * Journey J4 inside the app process: a vault exactly as a pre-envelope release left it, opened by
 * the upgraded build. The password login migrates it, Home shows the records, and the next launch
 * offers fingerprint because the migration created the biometric wrap.
 *
 * The device is reported as secured with a strong biometric ([DeviceSecurityStatus] replaced for this
 * class only), so the migration creates the Keystore wraps through the test gateway's JCE keys;
 * the CI image itself has neither a lock screen nor a fingerprint.
 */
// TODO(D3): legacy vault (crypto_version = 1) only
@HiltAndroidTest
@UninstallModules(TelemetryModule::class)
@RunWith(AndroidJUnit4::class)
class UpgradeMigrationE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @BindValue
    @JvmField
    val deviceSecurityStatus: DeviceSecurityStatus = FakeDeviceSecurityStatus()

    @BindValue
    @JvmField
    val vaultStorageStats: VaultStorageStats = AndroidVaultStorageStats(context)

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var keystoreGateway: FakeKeystoreGateway

    @Inject
    lateinit var fakeDeviceSecurityAuthProvider: FakeDeviceSecurityAuthProvider

    @Inject
    lateinit var sessionKeyHolder: SessionKeyHolder

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

    @Before
    fun setup() {
        hiltRule.inject()
        keystoreGateway.reset()
        fakeDeviceSecurityAuthProvider.reset()
        runBlocking {
            safeBoxDatabase.clearAllTables()
            val legacyKey = keystoreGateway.getOrCreateLegacyVaultKey()
            LegacyVaultFixtures.seedLegacyVault(
                database = safeBoxDatabase,
                legacyKey = legacyKey,
                password = E2ETestUtils.TEST_MASTER_PASSWORD,
                hint = E2ETestUtils.TEST_MASTER_HINT,
            )
            sessionKeyHolder.setLegacySession(legacyKey)
            E2ETestUtils.setupSeededVaultRecords(
                safeBoxDatabase,
                loginDataRepository,
                bankCardDataRepository,
                bankAccountDataRepository,
                secureNoteDataRepository,
                authenticatorDataRepository,
            )
            sessionKeyHolder.clearSessionKey()
            preferenceProvider.upsertIntPref(CommonConstants.ALLOWED_BIOMETRIC_LOGIN_COUNT_REMAINING, 3)
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
    fun legacyVault_passwordLogin_shouldMigrateShowTheRecordsAndOfferFingerprintOnTheNextLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            // A legacy vault has no biometric wrap: the notice shows and no prompt is raised.
            E2ETestUtils.waitForText(composeTestRule, context.getString(R.string.login_security_upgrade_notice))
            assertThat(fakeDeviceSecurityAuthProvider.invocationCount).isEqualTo(0)

            E2ETestUtils.unlockAppAndWaitForTitle(composeTestRule, context, "Apple ID Login")
            composeTestRule.onNodeWithText("Wifi Router Secrets").assertIsDisplayed()
            composeTestRule.onNodeWithText("GitHub Authenticator").assertIsDisplayed()
        }
        runBlocking {
            val meta = safeBoxDatabase.vaultMetaDao()
            assertThat(meta.get(VaultMetaKey.CRYPTO_VERSION)).isEqualTo(CryptoVersion.ENVELOPE)
            assertThat(meta.get(VaultMetaKey.LEGACY_PASSWORD_HASH)).isNull()
            assertThat(meta.get(VaultMetaKey.WRAPPED_DEK_BIOMETRIC)).isNotNull()
            assertThat(meta.get(VaultMetaKey.WRAPPED_DEK_DEVICE)).isNotNull()
        }
        assertThat(keystoreGateway.legacyVaultKey()).isNull()

        // Next launch after process death: the key is gone from RAM, and fingerprint opens the vault.
        VaultSessionTestBridge.lockVault()
        fakeDeviceSecurityAuthProvider.canAuthenticateOverride = true
        fakeDeviceSecurityAuthProvider.authHandlerOverride = { onSuccess, _ ->
            LaunchedEffect(Unit) { onSuccess(null) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntilNodeDisplayed(
                hasContentDescription(context.getString(R.string.cd_add_new_record_button)),
            )
            composeTestRule.waitUntilNodeDisplayed(hasText("Apple ID Login"))
            assertThat(fakeDeviceSecurityAuthProvider.lastCryptoObject?.cipher).isNotNull()
        }
    }
}
