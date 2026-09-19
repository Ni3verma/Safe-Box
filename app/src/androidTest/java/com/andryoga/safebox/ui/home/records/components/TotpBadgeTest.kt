package com.andryoga.safebox.ui.home.records.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.andryoga.safebox.R
import com.andryoga.safebox.test.fakes.FakeClipboard
import com.andryoga.safebox.test.fakes.FakeTotpGenerator
import com.andryoga.safebox.totp.engine.Base32Utils
import com.andryoga.safebox.totp.engine.interfaces.TotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Component-level Compose UI Test suite for [TotpBadge], the live code shown on a records list row.
 */
@RunWith(AndroidJUnit4::class)
class TotpBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard = FakeClipboard()

    @Before
    fun setup() {
        // Copying goes through the production rememberCopyToClipboardAction, which schedules the
        // clipboard clear via WorkManager.getInstance. AndroidManifest removes
        // WorkManagerInitializer, so without standing WorkManager up here this test would either
        // resolve a Hilt entry point it never registered, or silently inherit the instance left
        // behind by whichever Hilt test happened to run earlier in the same process.
        // SynchronousExecutor also keeps the scheduled clear out of the app's real work database.
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @Test
    fun validSeed_shouldDisplaySplitCodeCountdownSecondsAndCopyAffordance() {
        setBadgeContent(generator = FakeTotpGenerator(code = "123456", remainingSeconds = 17))

        composeTestRule.onNodeWithText("123 456").assertIsDisplayed()
        composeTestRule.onNodeWithText("17").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_totp_code))
            .assertIsDisplayed()
    }

    @Test
    fun tickerLoop_whenSecondAdvances_updatesDisplayedCodeAndCountdown() {
        val codeTicks = AtomicInteger(0)
        val remainingTicks = AtomicInteger(0)
        val steppingGenerator = object : TotpGenerator {
            override fun generateCode(config: TotpConfig, timeSeconds: Long): String =
                if (codeTicks.getAndIncrement() == 0) "111222" else "333444"

            override fun getRemainingSeconds(timeSeconds: Long, period: Int): Int =
                if (remainingTicks.getAndIncrement() == 0) 29 else 28

            override fun isValidSecret(secretBase32: String): Boolean = true

            override fun isValidConfig(config: TotpConfig): Boolean = true

            override fun normalizeSecret(secretBase32: String): String =
                Base32Utils.sanitize(secretBase32)
        }

        setBadgeContent(
            config = TotpConfig(secretKey = SECRET_KEY, period = 1),
            generator = steppingGenerator,
        )

        composeTestRule.onNodeWithText("111 222").assertIsDisplayed()
        composeTestRule.onNodeWithText("29").assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            codeTicks.get() >= 2
        }
        composeTestRule.onNodeWithText("333 444").assertIsDisplayed()
        composeTestRule.onNodeWithText("28").assertIsDisplayed()
    }

    @Test
    fun oddDigitCount_shouldSplitWithTheShorterHalfFirst() {
        setBadgeContent(
            config = TotpConfig(secretKey = SECRET_KEY, digits = 7),
            generator = FakeTotpGenerator(code = "1234567"),
        )

        composeTestRule.onNodeWithText("123 4567").assertIsDisplayed()
    }

    @Test
    fun eightDigitCode_shouldSplitIntoEqualHalves() {
        setBadgeContent(
            config = TotpConfig(secretKey = SECRET_KEY, digits = 8),
            generator = FakeTotpGenerator(code = "12345678"),
        )

        composeTestRule.onNodeWithText("1234 5678").assertIsDisplayed()
    }

    @Test
    fun invalidSeed_shouldDisplayErrorTextInsteadOfCodeAndCopyAffordance() {
        setBadgeContent(generator = FakeTotpGenerator(isValid = false))

        composeTestRule.onNodeWithText(context.getString(R.string.totp_invalid_secret_key))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_totp_code))
            .assertDoesNotExist()
    }

    @Test
    fun clickCopyIcon_shouldCopyUnformattedCodeAndInvokeCallback() {
        var copyClickCount = 0
        setBadgeContent(
            generator = FakeTotpGenerator(code = "123456"),
            onCopyClick = { copyClickCount++ },
        )

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_totp_code))
            .performClick()
        composeTestRule.waitForIdle()

        // the space is a display concern only; pasting it into an issuer's 2FA prompt would fail.
        assertThat(clipboard.lastCopiedText).isEqualTo("123456")
        assertThat(copyClickCount).isEqualTo(1)
    }

    private fun setBadgeContent(
        config: TotpConfig = TotpConfig(secretKey = SECRET_KEY),
        generator: TotpGenerator = FakeTotpGenerator(),
        onCopyClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SafeBoxTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    TotpBadge(
                        config = config,
                        onCopyClick = onCopyClick,
                        totpGenerator = generator,
                    )
                }
            }
        }
    }

    companion object {
        private const val SECRET_KEY = "JBSWY3DPEHPK3PXP"
    }
}
