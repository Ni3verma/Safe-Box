package com.andryoga.safebox.ui.login

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.di.FakeDeviceSecurityAuthProvider
import com.andryoga.safebox.security.envelope.migration.MigrationFailureReason
import com.andryoga.safebox.security.envelope.migration.MigrationProgressState
import com.andryoga.safebox.security.envelope.migration.MigrationTrigger
import com.andryoga.safebox.test.fakes.FakeHapticFeedback
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.SessionLockReason
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher

/**
 * Component-level UI Test suite for [LoginScreen].
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialScreenState_shouldShowWelcomeBackAndFields() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(hint = "Sample hint"),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.welcome_back)).assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsDisplayed()
    }

    @Test
    fun awayTimeoutLockReason_shouldShowTheAwayBanner() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(lockReason = SessionLockReason.AWAY_TIMEOUT),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsDisplayed()
    }

    @Test
    fun noLockReason_shouldShowNoBanner() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.login_away_banner)).assertDoesNotExist()
    }

    @Test
    fun emptyPassword_shouldDisableLoginButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        ).performTextReplacement("")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsNotEnabled()
    }

    @Test
    fun clickTogglePasswordIcon_shouldToggleVisualTransformation() {
        val testPassword = "SecretPass123"
        val maskedBullet = "\u2022".repeat(testPassword.length)

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextInput(testPassword)
        composeTestRule.waitForIdle()

        val toggleIconDescription = context.getString(R.string.cd_toggle_sensitive_data_visibility)
        composeTestRule.onNodeWithContentDescription(toggleIconDescription).assertIsDisplayed()
        composeTestRule.onNode(hasText(maskedBullet) and hasSetTextAction()).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(toggleIconDescription).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(toggleIconDescription).assertIsDisplayed()
        composeTestRule.onNode(hasText(testPassword) and hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun clickShowHint_shouldDisplayHintAndToggleToHideHint() {
        var showHintClicked = false
        val testHint = "My secret hint"

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(hint = testHint),
                    screenAction = { action ->
                        if (action is LoginScreenAction.ShowHintClicked) {
                            showHintClicked = true
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.show_hint)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(testHint).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.hide_hint)).assertIsDisplayed()
        assertThat(showHintClicked).isTrue()
    }

    @Test
    fun clickHideHint_shouldRemoveHintText() {
        val testHint = "My secret hint"

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(hint = testHint),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(testHint).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hint)).performClick()
        composeTestRule.onNodeWithText(testHint).assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.hide_hint)).performClick()
        composeTestRule.onNodeWithText(testHint).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hint)).assertIsDisplayed()
    }

    @Test
    fun incorrectPasswordState_shouldShowIncorrectPasswordErrorText() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.incorrect_pswrd_message))
            .assertIsDisplayed()
    }

    @Test
    fun failedLoginAttempt_shouldPerformRejectHapticOncePerRejection() {
        val haptic = FakeHapticFeedback()
        var uiState by mutableStateOf(LoginUiState())

        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = uiState,
                        screenAction = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        assertThat(haptic.performed).isEmpty()

        composeTestRule.runOnIdle {
            uiState = uiState.copy(
                userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
                failedLoginAttempts = 1,
            )
        }
        composeTestRule.waitForIdle()
        assertThat(haptic.performed).containsExactly(HapticFeedbackType.Reject)

        // A second wrong password leaves userAuthState unchanged; only the counter moves.
        composeTestRule.runOnIdle { uiState = uiState.copy(failedLoginAttempts = 2) }
        composeTestRule.waitForIdle()
        assertThat(haptic.performed)
            .containsExactly(HapticFeedbackType.Reject, HapticFeedbackType.Reject)
    }

    @Test
    fun screenCreatedAfterFailedAttempts_shouldNotReplayRejectHaptic() {
        val haptic = FakeHapticFeedback()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(
                            userAuthState = UserAuthState.INCORRECT_PASSWORD_ENTERED,
                            failedLoginAttempts = 2,
                        ),
                        screenAction = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(haptic.performed).isEmpty()
    }

    @Test
    fun clearPassword_shouldDisableLoginButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextInput("Secret@123")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsEnabled()

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextReplacement("")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsNotEnabled()
    }

    @Test
    fun clickLoginButtonWhenPasswordNotEmpty_shouldEmitLoginClickedAction() {
        var emittedPassword: String? = null
        val targetPassword = "MySecretPassword123"

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = { action ->
                        if (action is LoginScreenAction.LoginClicked) {
                            emittedPassword = action.password
                        }
                    }
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextInput(targetPassword)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedPassword).isEqualTo(targetPassword)
    }

    @Test
    fun rapidMultiClickOnLoginButton_shouldEmitClickAction() {
        var loginClickCount = 0
        val targetPassword = "MySecretPassword123"

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(),
                    screenAction = { action ->
                        if (action is LoginScreenAction.LoginClicked) {
                            loginClickCount++
                        }
                    }
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.password),
                substring = true
            )
        )
            .performTextInput(targetPassword)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsEnabled()

        // Perform rapid multi-clicks
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).performClick()
        composeTestRule.waitForIdle()

        // Verify that multiple click actions are emitted cleanly (or handled by state/ViewModel) without race condition crash
        assertThat(loginClickCount).isGreaterThan(0)
    }

    @Test
    fun legacyAuthenticationStep_whenDeviceCanAuthenticate_shouldPromptAndEmitLegacyResetAuthenticated() {
        val fakeAuthProvider = FakeDeviceSecurityAuthProvider().apply {
            canAuthenticateOverride = true
            authHandlerOverride = { onSuccess, _ ->
                onSuccess(null)
            }
        }
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides fakeAuthProvider) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(forgotPasswordStep = ForgotPasswordStep.LegacyAuthentication),
                        screenAction = { actions += it },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(actions).contains(LoginScreenAction.OnLegacyResetAuthenticated)
        // The legacy gate binds no cipher.
        assertThat(fakeAuthProvider.lastCryptoObject).isNull()
        composeTestRule.onNodeWithText(context.getString(R.string.device_security_required_title))
            .assertDoesNotExist()
    }

    @Test
    fun newPasswordStep_shouldShowUpdatePasswordDialogAndEmitResetPassword() {
        var emittedAction: LoginScreenAction? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(forgotPasswordStep = ForgotPasswordStep.NewPassword),
                    screenAction = { emittedAction = it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.update_password))
            .assertIsDisplayed()

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.new_password) + "*"
            )
        ).performTextInput("NewPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.confirm_new_password) + "*"
            )
        ).performTextInput("NewPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint) + "*"
            )
        ).performTextInput("updated hint")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(emittedAction).isInstanceOf(LoginScreenAction.OnResetPassword::class.java)
        val resetAction = emittedAction as LoginScreenAction.OnResetPassword
        assertThat(resetAction.newPassword).isEqualTo("NewPass@@123")
        assertThat(resetAction.hint).isEqualTo("updated hint")
    }

    @Test
    fun legacyAuthenticationStep_whenNoDeviceAuthEnrolled_shouldShowDeviceSecurityRequiredDialog() {
        val fakeAuthProvider = FakeDeviceSecurityAuthProvider().apply {
            canAuthenticateOverride = false
        }
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides fakeAuthProvider) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(forgotPasswordStep = ForgotPasswordStep.LegacyAuthentication),
                        screenAction = { actions += it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.device_security_required_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.open_settings))
            .assertIsDisplayed()
        assertThat(actions).contains(LoginScreenAction.OnDeviceSecurityRequiredDialogShown)

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(actions).contains(LoginScreenAction.OnDeviceSecurityRequiredDismissClicked)
    }

    @Test
    fun forgotPasswordClick_shouldOnlyEmitTheClickAndNeverShowTheDeviceSecurityDialog() {
        // A v2 vault must never see DeviceSecurityRequiredDialog (design §6.7); the ViewModel routes.
        val fakeAuthProvider = FakeDeviceSecurityAuthProvider().apply {
            canAuthenticateOverride = false
        }
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides fakeAuthProvider) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(),
                        screenAction = { actions += it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.forgot_password))
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(actions).contains(LoginScreenAction.ForgotPasswordClicked)
        composeTestRule.onNodeWithText(context.getString(R.string.device_security_required_title))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.update_password))
            .assertDoesNotExist()
    }

    @Test
    fun deviceAuthenticationStep_shouldBindTheCipherAndEmitRecoveryAuthenticatedWithIt() {
        val fakeAuthProvider = FakeDeviceSecurityAuthProvider().apply {
            canAuthenticateOverride = true
            authHandlerOverride = { onSuccess, _ ->
                onSuccess(null)
            }
        }
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides fakeAuthProvider) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(
                            forgotPasswordStep = ForgotPasswordStep.DeviceAuthentication(cipher),
                        ),
                        screenAction = { actions += it },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(fakeAuthProvider.lastCryptoObject?.cipher).isSameInstanceAs(cipher)
        val authenticated = actions.filterIsInstance<LoginScreenAction.OnRecoveryAuthenticated>().single()
        assertThat(authenticated.cipher).isSameInstanceAs(cipher)
    }

    @Test
    fun biometricUnlockCipher_shouldBindItAndEmitBiometricSuccessWithTheAuthorisedCipher() {
        val fakeAuthProvider = FakeDeviceSecurityAuthProvider().apply {
            canAuthenticateOverride = true
            authHandlerOverride = { onSuccess, _ ->
                onSuccess(null)
            }
        }
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDeviceSecurityAuthProvider provides fakeAuthProvider) {
                SafeBoxTheme {
                    LoginScreen(
                        uiState = LoginUiState(biometricUnlockCipher = cipher),
                        screenAction = { actions += it },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        assertThat(fakeAuthProvider.lastCryptoObject?.cipher).isSameInstanceAs(cipher)
        val success = actions.filterIsInstance<LoginScreenAction.BiometricSuccess>().single()
        assertThat(success.cipher).isSameInstanceAs(cipher)
        composeTestRule.onNodeWithText(context.getString(R.string.welcome_back)).assertIsDisplayed()
    }

    @Test
    fun legacyVaultState_shouldShowSecurityUpgradeNotice() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(isLegacyVault = true),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.login_security_upgrade_notice))
            .assertIsDisplayed()
    }

    @Test
    fun biometricKeyInvalidatedState_shouldShowTheBannerAboveThePasswordForm() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(isBiometricKeyInvalidated = true),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.login_biometric_invalidated_banner))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsDisplayed()
    }

    @Test
    fun recoveryFailureState_shouldShowTheReasonBannerAndKeepTheForm() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(recoveryFailure = RecoveryFailureReason.VERIFIER),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.recovery_failure_verifier))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertIsDisplayed()
    }

    @Test
    fun recoveryNotSetUpState_shouldReplaceTheFormAndOfferEraseAndBack() {
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(isRecoveryNotSetUp = true),
                    screenAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.recovery_not_set_up_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.recovery_not_set_up_body))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.recovery_not_set_up_restore_hint))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.device_security_required_title))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_action)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_title))
            .assertIsDisplayed()
        assertThat(actions).contains(LoginScreenAction.OnEraseVaultDialogShown)

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()
        assertThat(actions).contains(LoginScreenAction.OnEraseVaultCancelClicked)
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_title))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.recovery_back_to_login)).performClick()
        composeTestRule.waitForIdle()
        assertThat(actions).contains(LoginScreenAction.OnRecoveryNotSetUpDismissed)
    }

    @Test
    fun recoveryNotSetUpState_confirmingErase_shouldEmitConfirmAndCloseTheDialog() {
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(isRecoveryNotSetUp = true),
                    screenAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_action)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_confirm)).performClick()
        composeTestRule.waitForIdle()

        assertThat(actions).contains(LoginScreenAction.OnEraseVaultConfirmClicked)
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_title))
            .assertDoesNotExist()
    }

    @Test
    fun migrationRunningState_shouldShowProgressCardWithDeterminateCount() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(
                        isLegacyVault = true,
                        migrationProgress = MigrationProgressState.Running(done = 3, total = 10),
                    ),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_progress_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.vault_migration_progress_counter, 3, 10),
        ).assertIsDisplayed()
    }

    @Test
    fun migrationFailureForgotPasswordState_shouldShowForgotPasswordNoteAndHideContinueLegacyButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(
                        isLegacyVault = true,
                        migrationFailure = MigrationFailureUiState(
                            reason = MigrationFailureReason.DB_IO,
                            trigger = MigrationTrigger.FORGOT_PASSWORD,
                        ),
                    ),
                    screenAction = {},
                )
            }
        }

        composeTestRule.onNodeWithText(
            context.getString(R.string.vault_migration_failure_storage),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.vault_migration_failure_forgot_password_note),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_retry))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_continue_legacy))
            .assertDoesNotExist()
    }

    @Test
    fun migrationFailureKeystoreState_shouldShowBothActionsAndEmitRetryAndContinueEvents() {
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(
                        isLegacyVault = true,
                        migrationFailure = MigrationFailureUiState(
                            reason = MigrationFailureReason.KEYSTORE_TRANSIENT,
                            trigger = MigrationTrigger.LOGIN,
                        ),
                    ),
                    screenAction = { actions += it },
                )
            }
        }
        composeTestRule.waitForIdle()

        assertThat(
            actions.filterIsInstance<LoginScreenAction.OnMigrationFailureShown>().map { it.reason },
        ).contains(MigrationFailureReason.KEYSTORE_TRANSIENT)
        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_failure_keystore))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_retry))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.vault_migration_continue_legacy))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(actions).contains(LoginScreenAction.OnMigrationRetryClicked)
        assertThat(actions).contains(LoginScreenAction.OnMigrationContinueLegacyClicked)
    }

    @Test
    fun legacyKeyLostState_shouldShowLegacyKeyLostCardWithEraseInsteadOfLoginForm() {
        val actions = mutableListOf<LoginScreenAction>()

        composeTestRule.setContent {
            SafeBoxTheme {
                LoginScreen(
                    uiState = LoginUiState(userAuthState = UserAuthState.LEGACY_KEY_LOST),
                    screenAction = { actions += it },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.legacy_key_lost_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.legacy_key_lost_body))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.login))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_action)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_title))
            .assertIsDisplayed()
        assertThat(actions).contains(LoginScreenAction.OnEraseVaultDialogShown)
    }
}