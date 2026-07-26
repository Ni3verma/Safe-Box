package com.andryoga.safebox.ui.home.records.components

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level Compose UI Test suite for [NotificationPermissionRationaleDialog].
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class NotificationPermissionRationaleDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialDialogState_shouldDisplayHeadingBodyCheckboxAndButtons() {
        composeTestRule.setContent {
            SafeBoxTheme {
                NotificationPermissionRationaleDialog(
                    isNotificationPermissionAskedBefore = false,
                    onAllowClick = {},
                    onCancelClick = {},
                    dismissDialogAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.notification_permission_rationale_dialog_heading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.notification_permission_rationale_dialog_body))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.do_not_ask_again))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.allow))
            .assertIsDisplayed()
    }

    @Test
    fun clickCancelButton_shouldInvokeCancelCallbackWithCheckboxState() {
        var cancelClicked = false
        var doNotAskValue = false

        composeTestRule.setContent {
            SafeBoxTheme {
                NotificationPermissionRationaleDialog(
                    isNotificationPermissionAskedBefore = false,
                    onAllowClick = {},
                    onCancelClick = { doNotAsk ->
                        cancelClicked = true
                        doNotAskValue = doNotAsk
                    },
                    dismissDialogAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(cancelClicked).isTrue()
        assertThat(doNotAskValue).isFalse()
    }

    @Test
    fun toggleDoNotAskAgainCheckboxAndClickCancel_shouldPassTrueToCancelCallback() {
        var doNotAskValue = false

        composeTestRule.setContent {
            SafeBoxTheme {
                NotificationPermissionRationaleDialog(
                    isNotificationPermissionAskedBefore = false,
                    onAllowClick = {},
                    onCancelClick = { doNotAsk ->
                        doNotAskValue = doNotAsk
                    },
                    dismissDialogAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.do_not_ask_again)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(doNotAskValue).isTrue()
    }

    @Test
    fun clickAllowButton_shouldInvokeAllowCallback() {
        var allowClicked = false

        composeTestRule.setContent {
            SafeBoxTheme {
                NotificationPermissionRationaleDialog(
                    isNotificationPermissionAskedBefore = false,
                    onAllowClick = { _ ->
                        allowClicked = true
                    },
                    onCancelClick = {},
                    dismissDialogAction = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.allow)).performClick()
        composeTestRule.waitForIdle()

        assertThat(allowClicked).isTrue()
    }
}
