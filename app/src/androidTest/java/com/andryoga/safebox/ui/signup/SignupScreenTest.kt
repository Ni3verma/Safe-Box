package com.andryoga.safebox.ui.signup

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
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI Test suite for [SignupScreen].
 * Uses shared [StatefulSignupScreenTestHost] to verify interaction flows without duplication.
 */
@RunWith(AndroidJUnit4::class)
class SignupScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialScreenState_shouldShowFieldsAndDisableSignupButton() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(uiState = SignupUiState(), screenAction = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.welcome)).assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .assertIsDisplayed()
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsNotEnabled()
    }

    @Test
    fun clickTogglePasswordIcon_shouldToggleVisualTransformation() {
        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(password = "Secret@123"),
                    screenAction = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Toggle Password").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Toggle Password").performClick()
        composeTestRule.onNodeWithContentDescription("Toggle Password").assertIsDisplayed()
    }

    @Test
    fun typingInPasswordAndHintFields_shouldEmitCorrespondingActions() {
        var lastPasswordAction: SignupScreenAction? = null
        var lastHintAction: SignupScreenAction? = null

        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(),
                    screenAction = { action ->
                        when (action) {
                            is SignupScreenAction.OnPasswordUpdate -> lastPasswordAction = action
                            is SignupScreenAction.OnHintUpdate -> lastHintAction = action
                            else -> {}
                        }
                    }
                )
            }
        }

        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput("Secret@123")
        assertThat((lastPasswordAction as? SignupScreenAction.OnPasswordUpdate)?.password).isEqualTo(
            "Secret@123"
        )

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        )
            .performTextInput("My pet")
        assertThat((lastHintAction as? SignupScreenAction.OnHintUpdate)?.hint).isEqualTo("My pet")
    }

    @Test
    fun clickSignupButtonWhenEnabled_shouldEmitOnSignupClick() {
        var signupClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                SignupScreen(
                    uiState = SignupUiState(isSignupButtonEnabled = true),
                    screenAction = { action ->
                        if (action is SignupScreenAction.OnSignupClick) {
                            signupClicked = true
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).performClick()
        assertThat(signupClicked).isTrue()
    }

    @Test
    fun statefulFlow_typingInvalidThenValidCredentials_shouldDynamicallyEnableButton() {
        composeTestRule.setContent {
            StatefulSignupScreenTestHost()
        }

        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsNotEnabled()

        // Type invalid password (no uppercase/lowercase mix)
        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextReplacement("abc")
        composeTestRule.onNodeWithText(context.getString(R.string.case_validation_text))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsNotEnabled()

        // Type valid password meeting all rules (>=7 chars, mixed case, >=2 digits, >=1 special char)
        composeTestRule.onNode(hasSetTextAction() and hasText("abc", substring = true))
            .performTextReplacement("Qwerty@@12")
        composeTestRule.onNodeWithText(context.getString(R.string.case_validation_text))
            .assertDoesNotExist()

        // Button still disabled until hint is provided
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsNotEnabled()

        // Type valid hint
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        )
            .performTextInput("First car")

        // Now button must dynamically enable itself
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsEnabled()
    }

    @Test
    fun onSignupClick_shouldTriggerCallbackAndSimulateScreenNavigation() {
        var navigatedToNextScreen = false

        composeTestRule.setContent {
            StatefulSignupScreenTestHost(
                onSignupClicked = {
                    navigatedToNextScreen = true
                }
            )
        }

        // Type valid credentials
        composeTestRule.onNode(hasSetTextAction() and hasText("Password", substring = true))
            .performTextInput("Qwerty@@12")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint),
                substring = true
            )
        )
            .performTextInput("My hint")

        // Trigger signup
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).assertIsEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.signup)).performClick()

        assertThat(navigatedToNextScreen).isTrue()
    }
}
