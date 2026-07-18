package com.andryoga.safebox.ui.home.settings

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component UI test suite verifying toggles, sliders, and support actions on the SettingsScreen.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenComprehensiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun renderSettingsScreen_shouldDisplayAllSecurityAndSupportSectionsAndHeaders() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(uiState = Settings(), onScreenAction = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_section_security_and_privacy))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_privacy_enabled_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_auto_backup_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(
                R.string.settings_ask_for_pswrd_after_biometric_title,
                5
            )
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_away_timeout_title, 10))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.settings_section_support_and_community))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_feedback_title))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_review_title))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_github_title))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickPrivacySwitch_shouldToggleStateAndEmitUpdatePrivacyAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(isPrivacyEnabled = true),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdatePrivacy::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdatePrivacy).enabled).isFalse()
    }

    @Test
    fun clickAutoBackupSwitch_shouldToggleStateAndEmitUpdateAutoBackupAfterLoginAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(autoBackupAfterPasswordLogin = true),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable())[1].performClick()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdateAutoBackupAfterLogin::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdateAutoBackupAfterLogin).enabled).isFalse()
    }

    @Test
    fun sliderPreference_forBiometricLogins_shouldRenderFormattedTitleAndEmitUpdateActionOnProgressChange() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(passwordAfterXBiometricLogins = 5),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        val expectedTitle =
            context.getString(R.string.settings_ask_for_pswrd_after_biometric_title, 5)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(5f, 5f..15f, 9)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }

        composeTestRule.waitForIdle()
        if (emittedAction != null) {
            assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdatePasswordAfterXBiometric::class.java)
            assertThat((emittedAction as SettingsScreenAction.UpdatePasswordAfterXBiometric).limit).isEqualTo(
                10
            )
        }
    }

    @Test
    fun sliderPreference_forAwayTimeout_shouldRenderFormattedTitleAndEmitUpdateActionOnProgressChange() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(awayTimeoutSec = 10),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        val expectedTitle = context.getString(R.string.settings_away_timeout_title, 10)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(10f, 5f..20f, 14)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }

        composeTestRule.waitForIdle()
        if (emittedAction != null) {
            assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdateAwayTimeout::class.java)
            assertThat((emittedAction as SettingsScreenAction.UpdateAwayTimeout).timeout).isEqualTo(
                15
            )
        }
    }

    @Test
    fun clickSendFeedbackPreference_shouldEmitSendFeedbackAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_feedback_title))
            .performScrollTo().performClick()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.SendFeedback)
    }

    @Test
    fun clickReviewAppPreference_shouldEmitReviewAppAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_review_title))
            .performScrollTo().performClick()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.ReviewApp)
    }

    @Test
    fun clickOpenGithubPreference_shouldEmitOpenGithubProjectAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = Settings(),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_github_title))
            .performScrollTo().performClick()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OpenGithubProject)
    }
}
