package com.andryoga.safebox.ui.loading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.data.db.SafeBoxDatabase
import com.andryoga.safebox.data.db.entity.VaultMetaEntity
import com.andryoga.safebox.data.db.models.CryptoVersion
import com.andryoga.safebox.data.db.models.VaultMetaKey
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.e2e.E2ETestUtils
import com.andryoga.safebox.providers.interfaces.EncryptedPreferenceProvider
import com.andryoga.safebox.providers.interfaces.PreferenceProvider
import com.andryoga.safebox.ui.MainActivity
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
 * Integration UI Test suite for [LoadingScreenRoot] (Splash Screen & Boot Routing).
 *
 * The start destination is decided by Room, never by `IS_SIGN_UP_REQUIRED`
 * (`docs/design/envelope-encryption.md` §3.6): every test below writes the preference so that it
 * contradicts the database, so a routing that still reads the preference fails.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LoadingScreenRootTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var safeBoxDatabase: SafeBoxDatabase

    @Inject
    lateinit var userDetailsRepository: UserDetailsRepository

    @Inject
    lateinit var encryptedPreferenceProvider: EncryptedPreferenceProvider

    @Inject
    lateinit var preferenceProvider: PreferenceProvider

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalSignupRequired: Boolean = false

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            originalSignupRequired =
                encryptedPreferenceProvider.getBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, false)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            encryptedPreferenceProvider.upsertBooleanPref(
                CommonConstants.IS_SIGN_UP_REQUIRED,
                originalSignupRequired,
            )
        }
    }

    @Test
    fun bootWithNoVault_shouldNavigateToSignupRoute_evenWhenPrefSaysSignupNotRequired() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, false)
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            awaitText(R.string.welcome)
        }
    }

    @Test
    fun bootWithLegacyUserRow_shouldNavigateToLoginRoute_evenWhenPrefSaysSignupRequired() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                encryptedPreferenceProvider,
                preferenceProvider,
            )
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            awaitText(R.string.welcome_back)
        }
    }

    @Test
    fun bootWithCryptoVersionRowOnly_shouldNavigateToLoginRoute_evenWhenPrefSaysSignupRequired() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            safeBoxDatabase.vaultMetaDao()
                .upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
            encryptedPreferenceProvider.upsertBooleanPref(CommonConstants.IS_SIGN_UP_REQUIRED, true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            awaitText(R.string.welcome_back)
        }
    }

    private fun awaitText(stringRes: Int) {
        val text = context.getString(stringRes)
        composeTestRule.waitUntil(timeoutMillis = 10000L) {
            runCatching {
                composeTestRule.onAllNodes(hasText(text), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }
}
