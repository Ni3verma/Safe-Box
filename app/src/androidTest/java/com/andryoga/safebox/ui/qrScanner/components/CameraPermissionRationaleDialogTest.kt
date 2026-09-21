package com.andryoga.safebox.ui.qrScanner.components

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
 * Component-level Compose UI Test suite for [CameraPermissionRationaleDialog].
 *
 * The confirm button changes both its label and its callback once the permission can no longer be
 * requested, so every test here pins one of the two states explicitly.
 */
@RunWith(AndroidJUnit4::class)
class CameraPermissionRationaleDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deniedOnce_shouldDisplayHeadingBodyAndAllowConfirmButton() {
        setDialogContent(isPermanentlyDenied = false)

        composeTestRule.onNodeWithText(
            context.getString(R.string.camera_permission_rationale_dialog_heading),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.getString(R.string.camera_permission_rationale_dialog_body),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.allow)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.open_settings))
            .assertDoesNotExist()
    }

    @Test
    fun permanentlyDenied_shouldReplaceAllowWithOpenSettings() {
        setDialogContent(isPermanentlyDenied = true)

        // re-requesting is a no-op once the system stops prompting, so the only way out is
        // the app settings page.
        composeTestRule.onNodeWithText(context.getString(R.string.open_settings))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.allow)).assertDoesNotExist()
    }

    @Test
    fun clickAllow_shouldInvokeAllowCallbackOnly() {
        var allowClicks = 0
        var openSettingsClicks = 0
        setDialogContent(
            isPermanentlyDenied = false,
            onAllowClick = { allowClicks++ },
            onOpenSettingsClick = { openSettingsClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.allow)).performClick()
        composeTestRule.waitForIdle()

        assertThat(allowClicks).isEqualTo(1)
        assertThat(openSettingsClicks).isEqualTo(0)
    }

    @Test
    fun clickOpenSettings_shouldInvokeOpenSettingsCallbackOnly() {
        var allowClicks = 0
        var openSettingsClicks = 0
        setDialogContent(
            isPermanentlyDenied = true,
            onAllowClick = { allowClicks++ },
            onOpenSettingsClick = { openSettingsClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.open_settings)).performClick()
        composeTestRule.waitForIdle()

        assertThat(openSettingsClicks).isEqualTo(1)
        assertThat(allowClicks).isEqualTo(0)
    }

    @Test
    fun clickCancel_shouldInvokeCancelCallback() {
        var cancelClicks = 0
        setDialogContent(isPermanentlyDenied = false, onCancelClick = { cancelClicks++ })

        composeTestRule.onNodeWithText(context.getString(R.string.common_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertThat(cancelClicks).isEqualTo(1)
    }

    private fun setDialogContent(
        isPermanentlyDenied: Boolean,
        onAllowClick: () -> Unit = {},
        onOpenSettingsClick: () -> Unit = {},
        onCancelClick: () -> Unit = {},
        dismissDialogAction: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SafeBoxTheme {
                CameraPermissionRationaleDialog(
                    isPermanentlyDenied = isPermanentlyDenied,
                    onAllowClick = onAllowClick,
                    onOpenSettingsClick = onOpenSettingsClick,
                    onCancelClick = onCancelClick,
                    dismissDialogAction = dismissDialogAction,
                )
            }
        }
    }
}
