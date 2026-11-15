package com.andryoga.safebox.ui.loading

import android.net.Uri
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
import com.andryoga.safebox.data.repository.interfaces.BackupMetadataRepository
import com.andryoga.safebox.data.repository.interfaces.UserDetailsRepository
import com.andryoga.safebox.e2e.E2ETestUtils
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
 * The start destination is decided by the vault tables in Room alone (`VaultRepository.hasVault()`).
 * The signup case seeds every other kind of state a previous vault leaves behind, so a routing that
 * reads anything but the vault tables fails.
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
    lateinit var preferenceProvider: PreferenceProvider

    @Inject
    lateinit var backupMetadataRepository: BackupMetadataRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            preferenceProvider.upsertBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, false)
        }
    }

    @Test
    fun bootWithNoVault_shouldNavigateToSignupRoute_evenWithStateLeftByAPreviousVault() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            // A backup folder row and the nudge preference outlive an erased vault; neither is a vault.
            backupMetadataRepository.insertBackupMetadata(Uri.parse("file:///sdcard/SafeboxBackups"))
            preferenceProvider.upsertBooleanPref(CommonConstants.IS_SCREEN_LOCK_NUDGE_DISMISSED, true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            awaitText(R.string.welcome)
        }
    }

    @Test
    fun bootWithEnvelopeVault_shouldNavigateToLoginRoute() {
        runBlocking {
            E2ETestUtils.setupUnlockedHomeState(
                safeBoxDatabase,
                userDetailsRepository,
                preferenceProvider,
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            awaitText(R.string.welcome_back)
        }
    }

    @Test
    fun bootWithCryptoVersionRowOnly_shouldNavigateToLoginRoute() {
        runBlocking {
            safeBoxDatabase.clearAllTables()
            safeBoxDatabase.vaultMetaDao()
                .upsert(VaultMetaEntity(VaultMetaKey.CRYPTO_VERSION, CryptoVersion.LEGACY))
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
