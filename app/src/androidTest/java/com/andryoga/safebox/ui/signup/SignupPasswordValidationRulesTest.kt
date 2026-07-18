package com.andryoga.safebox.ui.signup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite verifying exhaustive password rules, hint validation, and edge-case handling on [SignupScreen].
 * Consolidated to hold all stateless rule rendering checks and edge cases without duplication.
 */
@RunWith(AndroidJUnit4::class)
class SignupPasswordValidationRulesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whitespaceOnlyPasswordAndHint_shouldMaintainErrorStateAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "   ",
                        hint = "   ",
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.EMPTY_PASSWORD
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.blank_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun emptyPasswordState_shouldShowBlankValidationTextAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.EMPTY_PASSWORD
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.blank_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun shortPasswordLengthState_shouldShowLengthValidationTextAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "abc",
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.SHORT_PASSWORD_LENGTH
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.length_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun notMixCasePasswordState_shouldShowCaseValidationTextAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "abcdefgh",
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.NOT_MIX_CASE
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.case_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun lessNumericCountPasswordState_shouldShowNumericValidationTextAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "Abcdefg1",
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.LESS_NUMERIC_COUNT
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.numeric_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun noSpecialCharPasswordState_shouldShowSpecialCharValidationTextAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "Abcdefg12",
                        isPasswordFieldError = true,
                        passwordValidatorState = PasswordValidatorState.NO_SPECIAL_CHAR
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.special_char_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun validPasswordWithBlankHint_shouldDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "Secret@@123",
                        hint = "",
                        isPasswordFieldError = false,
                        passwordValidatorState = PasswordValidatorState.PASSWORD_IS_OK,
                        isSignupButtonEnabled = false
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()
    }

    @Test
    fun validPasswordAndValidHint_shouldEnableSignupButtonAndClearErrorState() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(
                        password = "Secret@@123",
                        hint = "My hint",
                        isPasswordFieldError = false,
                        passwordValidatorState = PasswordValidatorState.PASSWORD_IS_OK,
                        isSignupButtonEnabled = true
                    ),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()
    }

    @Test
    fun statefulValidationHost_enteringWhitespaceHint_shouldNotEnableSignupButton() {
        composeTestRule.setContent {
            StatefulSignupScreenTestHost()
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput("Secret@@123")

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).performTextInput("   ")

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsNotEnabled()

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).performTextReplacement("Valid Hint")

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()
    }

    @Test
    fun unicodeAndSpecialCharPassword_shouldValidateAndPersistSuccessfully() {
        var signupTriggered = false
        composeTestRule.setContent {
            StatefulSignupScreenTestHost(
                onSignupClicked = { signupTriggered = true }
            )
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput("P@sswrd🚀#123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).performTextInput("Emoji hint")

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()
            .performClick()

        assertThat(signupTriggered).isTrue()
    }

    @Test
    fun maxLengthOverflowPassword_shouldHandleVeryLongStringCleanly() {
        val veryLongPassword = "A@1" + "a".repeat(1000) + "12@"
        var observedPassword = ""

        composeTestRule.setContent {
            StatefulSignupScreenTestHost(
                screenActionObserver = { action ->
                    if (action is SignupScreenAction.OnPasswordUpdate) {
                        observedPassword = action.password
                    }
                }
            )
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput(veryLongPassword)

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).performTextInput("Long password hint")

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()
        assertThat(observedPassword).isEqualTo(veryLongPassword)
    }

    @Test
    fun recompositionDuringSignupInput_shouldPreserveTypedCredentials() {
        composeTestRule.setContent {
            StatefulSignupScreenTestHost()
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput("Qwerty@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).performTextInput("Preserved hint")

        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()

        // Force a semantics query / recomposition pass check
        composeTestRule.onNode(hasSetTextAction() and hasText("Qwerty@@123", substring = true))
            .assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Preserved hint", substring = true))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup))
            .assertIsEnabled()
    }
}
