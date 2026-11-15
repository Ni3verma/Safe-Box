package com.andryoga.safebox.ui.login.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level UI tests for [EraseVaultDialog]: the show / cancel / confirm triad the
 * ViewModel turns into analytics and the erase itself.
 */
@RunWith(AndroidJUnit4::class)
class EraseVaultDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dialog_shouldShowTitleBodyAndBothActionsAndReportShowOnce() {
        var showCount = 0

        composeTestRule.setContent {
            SafeBoxTheme {
                EraseVaultDialog(
                    onDismiss = {},
                    onConfirm = {},
                    onShow = { showCount++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_confirm)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).assertIsDisplayed()
        assertThat(showCount).isEqualTo(1)
    }

    @Test
    fun clickCancel_shouldInvokeOnDismissOnly() {
        var dismissed = false
        var confirmed = false

        composeTestRule.setContent {
            SafeBoxTheme {
                EraseVaultDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissed).isTrue()
        assertThat(confirmed).isFalse()
    }

    @Test
    fun clickErase_shouldInvokeOnConfirmOnly() {
        var dismissed = false
        var confirmed = false

        composeTestRule.setContent {
            SafeBoxTheme {
                EraseVaultDialog(
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = true },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.erase_vault_dialog_confirm)).performClick()
        composeTestRule.waitForIdle()

        assertThat(confirmed).isTrue()
        assertThat(dismissed).isFalse()
    }
}
