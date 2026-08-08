package com.andryoga.safebox.ui.core.password

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdatePasswordDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialState_saveButtonShouldBeDisabled() {
        composeTestRule.setContent {
            SafeBoxTheme {
                UpdatePasswordDialog(
                    onDismiss = {},
                    onSave = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsNotEnabled()
    }

    @Test
    fun invalidPassword_shouldShowValidationErrorAndSaveDisabled() {
        composeTestRule.setContent {
            SafeBoxTheme {
                UpdatePasswordDialog(
                    onDismiss = {},
                    onSave = { _, _ -> }
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.new_password) + "*"
            )
        ).performTextInput("Ab@12")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.length_validation_text))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsNotEnabled()
    }

    @Test
    fun mismatchedConfirmPassword_shouldShowMismatchErrorAndSaveDisabled() {
        composeTestRule.setContent {
            SafeBoxTheme {
                UpdatePasswordDialog(
                    onDismiss = {},
                    onSave = { _, _ -> }
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.new_password) + "*"
            )
        ).performTextInput("ValidPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.confirm_new_password) + "*"
            )
        ).performTextInput("DifferentPass@@456")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint) + "*"
            )
        ).performTextInput("my hint")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.passwords_do_not_match))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsNotEnabled()
    }

    @Test
    fun validPasswordAndHint_saveButtonShouldBeEnabledAndTriggerCallback() {
        var savedPassword = ""
        var savedHint = ""
        composeTestRule.setContent {
            SafeBoxTheme {
                UpdatePasswordDialog(
                    onDismiss = {},
                    onSave = { pswrd, hnt ->
                        savedPassword = pswrd
                        savedHint = hnt
                    }
                )
            }
        }

        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.new_password) + "*"
            )
        ).performTextInput("ValidPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.confirm_new_password) + "*"
            )
        ).performTextInput("ValidPass@@123")
        composeTestRule.onNode(
            hasSetTextAction() and hasText(
                context.getString(R.string.hint) + "*"
            )
        ).performTextInput("my hint")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.confirm))
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(savedPassword).isEqualTo("ValidPass@@123")
        assertThat(savedHint).isEqualTo("my hint")
    }

    @Test
    fun cancelButtonClick_shouldTriggerOnDismiss() {
        var dismissCalled = false

        composeTestRule.setContent {
            SafeBoxTheme {
                UpdatePasswordDialog(
                    onDismiss = { dismissCalled = true },
                    onSave = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissCalled).isTrue()
    }
}
