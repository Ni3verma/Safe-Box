package com.andryoga.safebox.ui.home.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.di.FakeDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.TestTags
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component UI test suite verifying toggles, sliders, and support actions on the SettingsScreen,
 * plus each change-password step, the legacy-session state and the fingerprint-unlock switch.
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
                SettingsScreen(uiState = SettingsUiState(), onScreenAction = {})
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
        composeTestRule.onNodeWithText(context.getString(R.string.settings_support_title))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickPrivacySwitch_shouldToggleStateAndEmitUpdatePrivacyAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings(isPrivacyEnabled = true)),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdatePrivacy::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdatePrivacy).enabled).isFalse()
    }

    @Test
    fun clickAutoBackupSwitch_shouldToggleStateAndEmitUpdateAutoBackupAfterLoginAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings(autoBackupAfterPasswordLogin = true)),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onAllNodes(isToggleable())[1].performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdateAutoBackupAfterLogin::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdateAutoBackupAfterLogin).enabled).isFalse()
    }

    @Test
    fun sliderPreference_forBiometricLimit_shouldRenderFormattedTitleAndEmitUpdateActionOnProgressChange() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings(passwordAfterXBiometricLogins = 5)),
                    onScreenAction = { emittedAction = it },
                )
            }
        }

        val expectedTitle =
            context.getString(R.string.settings_ask_for_pswrd_after_biometric_title, 5)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(5f, 5f..15f, 9)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }

        composeTestRule.waitForIdle()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdatePasswordAfterXBiometric::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdatePasswordAfterXBiometric).limit).isEqualTo(
            10
        )
    }

    @Test
    fun sliderPreference_forAwayTimeout_shouldRenderFormattedTitleAndEmitUpdateActionOnProgressChange() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings(awayTimeoutSec = 10)),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        val expectedTitle = context.getString(R.string.settings_away_timeout_title, 10)
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(10f, 5f..20f, 14)))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }

        composeTestRule.waitForIdle()
        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdateAwayTimeout::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdateAwayTimeout).timeout).isEqualTo(15)
    }

    @Test
    fun clickSendFeedbackPreference_shouldEmitSendFeedbackAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings()),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_feedback_title))
            .performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.SendFeedback)
    }

    @Test
    fun clickReviewAppPreference_shouldEmitReviewAppAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings()),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_review_title))
            .performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.ReviewApp)
    }

    @Test
    fun clickOpenGithubPreference_shouldEmitOpenGithubProjectAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings()),
                    onScreenAction = { emittedAction = it }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_github_title))
            .performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OpenGithubProject)
    }

    @Test
    fun clickBuyMeACoffeePreference_shouldEmitOpenBuyMeACoffeeAction() {
        var emittedAction: SettingsScreenAction? = null
        composeTestRule.setContent {
            SafeBoxTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = Settings()),
                    onScreenAction = { emittedAction = it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_support_title))
            .performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OpenBuyMeACoffee)
    }

    private fun setScreen(
        uiState: SettingsUiState,
        authProvider: FakeDeviceSecurityAuthProvider = FakeDeviceSecurityAuthProvider(),
        onScreenAction: (SettingsScreenAction) -> Unit,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides authProvider) {
                SafeBoxTheme {
                    SettingsScreen(uiState = uiState, onScreenAction = onScreenAction)
                }
            }
        }
    }

    @Test
    fun changeMasterPasswordClick_shouldReportWhetherTheDevicePromptCanBeUsed() {
        val emitted = mutableListOf<SettingsScreenAction>()
        val authProvider = FakeDeviceSecurityAuthProvider().apply { canAuthenticateOverride = true }
        setScreen(SettingsUiState(), authProvider) { emitted += it }

        composeTestRule.onNodeWithText(context.getString(R.string.change_master_password))
            .performScrollTo()
            .performClick()
        authProvider.canAuthenticateOverride = false
        composeTestRule.onNodeWithText(context.getString(R.string.change_master_password))
            .performClick()
        composeTestRule.waitForIdle()

        val clicks = emitted.filterIsInstance<SettingsScreenAction.ChangePasswordClicked>()
        assertThat(clicks.map { it.canUseDeviceAuth }).containsExactly(true, false).inOrder()
        // No screen lock no longer means the device-security dialog: the master password gates it.
        composeTestRule.onNodeWithText(context.getString(R.string.device_security_required_title))
            .assertDoesNotExist()
    }

    @Test
    fun deviceAuthenticationStep_whenThePromptSucceeds_shouldReportIt() {
        var emittedAction: SettingsScreenAction? = null
        val authProvider = FakeDeviceSecurityAuthProvider().apply {
            authHandlerOverride = { onSuccess, _ -> onSuccess(null) }
        }

        setScreen(
            SettingsUiState(changePasswordStep = ChangePasswordStep.DeviceAuthentication),
            authProvider,
        ) { emittedAction = it }
        composeTestRule.waitForIdle()

        assertThat(authProvider.invocationCount).isAtLeast(1)
        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OnDeviceAuthSucceeded)
    }

    @Test
    fun deviceAuthenticationStep_whenThePromptIsCancelled_shouldReportItForTheMasterPasswordFallback() {
        var emittedAction: SettingsScreenAction? = null
        val authProvider = FakeDeviceSecurityAuthProvider().apply {
            authHandlerOverride = { _, onErrorOrCancel -> onErrorOrCancel() }
        }

        setScreen(
            SettingsUiState(changePasswordStep = ChangePasswordStep.DeviceAuthentication),
            authProvider,
        ) { emittedAction = it }
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OnDeviceAuthFailedOrCancelled)
    }

    @Test
    fun currentPasswordStep_shouldSubmitTheTypedPassword() {
        var emittedAction: SettingsScreenAction? = null
        setScreen(SettingsUiState(changePasswordStep = ChangePasswordStep.CurrentPassword())) {
            emittedAction = it
        }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_current_password_title))
            .assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.settings_current_password_label)),
        ).performTextInput("Current@@123")
        composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.OnCurrentPasswordSubmitted::class.java)
        assertThat((emittedAction as SettingsScreenAction.OnCurrentPasswordSubmitted).password)
            .isEqualTo("Current@@123")
    }

    @Test
    fun currentPasswordStep_afterAWrongPassword_shouldSayItIsIncorrectAndStay() {
        var emittedAction: SettingsScreenAction? = null
        setScreen(
            SettingsUiState(changePasswordStep = ChangePasswordStep.CurrentPassword(isIncorrect = true)),
        ) { emittedAction = it }

        composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isEqualTo(SettingsScreenAction.OnCurrentPasswordDismissed)
    }

    @Test
    fun currentPasswordStep_whileVerifying_shouldDisableConfirm() {
        setScreen(
            SettingsUiState(changePasswordStep = ChangePasswordStep.CurrentPassword(isVerifying = true)),
        ) { }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.settings_current_password_label)),
        ).performTextInput("Current@@123")

        composeTestRule.onNodeWithText(context.getString(R.string.confirm)).assertIsNotEnabled()
    }

    @Test
    fun newPasswordStep_shouldEmitTheNewPasswordAndHint() {
        var emittedAction: SettingsScreenAction? = null
        setScreen(SettingsUiState(changePasswordStep = ChangePasswordStep.NewPassword)) { emittedAction = it }

        composeTestRule.onNodeWithText(context.getString(R.string.update_password))
            .assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.new_password) + "*"),
        ).performTextInput("NewPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.confirm_new_password) + "*"),
        ).performTextInput("NewPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(context.getString(R.string.hint) + "*"),
        ).performTextInput("updated hint")
        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.OnUpdateMasterPassword::class.java)
        val updateAction = emittedAction as SettingsScreenAction.OnUpdateMasterPassword
        assertThat(updateAction.newPassword).isEqualTo("NewPass@@123")
        assertThat(updateAction.hint).isEqualTo("updated hint")
    }

    @Test
    fun legacySession_shouldDisableChangePasswordAndExplainWhy() {
        val emitted = mutableListOf<SettingsScreenAction>()
        setScreen(SettingsUiState(isLegacySession = true)) { emitted += it }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_change_password_legacy_body))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.change_master_password))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emitted.filterIsInstance<SettingsScreenAction.ChangePasswordClicked>()).isEmpty()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_biometric_unlock_title))
            .assertDoesNotExist()
    }

    @Test
    fun biometricSwitch_whenHidden_shouldNotRender() {
        setScreen(SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Hidden)) { }

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH).assertDoesNotExist()
    }

    @Test
    fun biometricSwitch_whenUnavailable_shouldBeDisabledWithTheReason() {
        setScreen(SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Unavailable)) { }

        composeTestRule.onNodeWithText(context.getString(R.string.settings_biometric_unlock_unavailable_body))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH).assertIsNotEnabled()
    }

    @Test
    fun biometricSwitch_whenOn_shouldEmitTurnOffOnClick() {
        var emittedAction: SettingsScreenAction? = null
        setScreen(SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Available(isOn = true))) {
            emittedAction = it
        }

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH)
            .assertIsOn()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isInstanceOf(SettingsScreenAction.UpdateBiometricUnlock::class.java)
        assertThat((emittedAction as SettingsScreenAction.UpdateBiometricUnlock).enabled).isFalse()
    }

    @Test
    fun biometricSwitch_whenOff_shouldEmitTurnOnOnClick() {
        var emittedAction: SettingsScreenAction? = null
        setScreen(SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Available(isOn = false))) {
            emittedAction = it
        }

        composeTestRule.onNodeWithTag(TestTags.SETTINGS_BIOMETRIC_UNLOCK_SWITCH)
            .assertIsOff()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat((emittedAction as SettingsScreenAction.UpdateBiometricUnlock).enabled).isTrue()
    }
}
