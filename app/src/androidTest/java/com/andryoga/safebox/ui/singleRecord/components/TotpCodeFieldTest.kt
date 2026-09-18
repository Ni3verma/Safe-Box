package com.andryoga.safebox.ui.singleRecord.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.test.fakes.FakeClipboard
import com.andryoga.safebox.test.fakes.FakeTotpGenerator
import com.andryoga.safebox.totp.models.TotpConfig
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level Compose UI Test suite for [TotpCodeField], the live code shown on the record
 * detail screen.
 */
@RunWith(AndroidJUnit4::class)
class TotpCodeFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard = FakeClipboard()

    @Test
    fun validSeed_shouldDisplayLabelSplitCodeAndCopyAffordance() {
        setFieldContent(generator = FakeTotpGenerator(code = "123456"))

        composeTestRule.onNodeWithText(context.getString(R.string.totp_code)).assertIsDisplayed()
        composeTestRule.onNodeWithText("123 456").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_totp_code))
            .assertIsDisplayed()
    }

    @Test
    fun oddDigitCount_shouldSplitWithTheShorterHalfFirst() {
        setFieldContent(
            config = TotpConfig(secretKey = SECRET_KEY, digits = 7),
            generator = FakeTotpGenerator(code = "1234567"),
        )

        composeTestRule.onNodeWithText("123 4567").assertIsDisplayed()
    }

    @Test
    fun invalidSeed_shouldKeepLabelAndDegradeToErrorTextWithoutCopyAffordance() {
        setFieldContent(generator = FakeTotpGenerator(isValid = false))

        // the label stays so the record still reads as a normal field the user can leave or delete.
        composeTestRule.onNodeWithText(context.getString(R.string.totp_code)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.totp_invalid_secret_key))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_copy_totp_code))
            .assertDoesNotExist()
    }

    @Test
    fun invalidSeed_shouldNotBeClickable() {
        setFieldContent(generator = FakeTotpGenerator(isValid = false))

        composeTestRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun clickField_shouldCopyUnformattedCodeAndInvokeCallback() {
        var copyClickCount = 0
        setFieldContent(
            generator = FakeTotpGenerator(code = "123456"),
            onCopyClick = { copyClickCount++ },
        )

        composeTestRule.onNode(hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        // the space is a display concern only; pasting it into an issuer's 2FA prompt would fail.
        assertThat(clipboard.lastCopiedText).isEqualTo("123456")
        assertThat(copyClickCount).isEqualTo(1)
    }

    private fun setFieldContent(
        config: TotpConfig = TotpConfig(secretKey = SECRET_KEY),
        generator: FakeTotpGenerator = FakeTotpGenerator(),
        onCopyClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SafeBoxTheme {
                CompositionLocalProvider(LocalClipboard provides clipboard) {
                    TotpCodeField(
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
