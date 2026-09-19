package com.andryoga.safebox.ui.qrScanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.andryoga.safebox.R
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Component-level Compose UI Test suite for [QrScannerViewfinderContent].
 *
 * The enclosing `QrScannerScreen` reads the real permission state and binds CameraX, neither of
 * which a test can drive. This composable is the seam below it: the camera is a slot, so every
 * overlay control and dialog can be exercised with the slot left empty.
 */
@RunWith(AndroidJUnit4::class)
class QrScannerViewfinderContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun defaultState_shouldDisplayTitleInstructionAndManualEntryButton() {
        setViewfinderContent()

        composeTestRule.onNodeWithText(context.getString(R.string.qr_scanner_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.qr_scanner_instruction))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.enter_key_manually))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.close))
            .assertIsDisplayed()
    }

    @Test
    fun cameraWithoutFlashUnit_shouldHideTorchControl() {
        setViewfinderContent(QrScannerUiState(hasFlashUnit = false))

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.flash_toggle_description),
        ).assertDoesNotExist()
    }

    @Test
    fun cameraWithFlashUnit_shouldShowTorchControlAndReportToggle() {
        var toggleClicks = 0
        setViewfinderContent(
            uiState = QrScannerUiState(hasFlashUnit = true),
            onToggleTorch = { toggleClicks++ },
        )

        composeTestRule.onNodeWithContentDescription(
            context.getString(R.string.flash_toggle_description),
        ).performClick()
        composeTestRule.waitForIdle()

        assertThat(toggleClicks).isEqualTo(1)
    }

    @Test
    fun clickClose_shouldInvokeCloseCallback() {
        var closeClicks = 0
        setViewfinderContent(onClose = { closeClicks++ })

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.close))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(closeClicks).isEqualTo(1)
    }

    @Test
    fun clickEnterKeyManually_shouldInvokeManualEntryCallback() {
        var manualEntryClicks = 0
        setViewfinderContent(onEnterKeyManually = { manualEntryClicks++ })

        composeTestRule.onNodeWithText(context.getString(R.string.enter_key_manually))
            .performClick()
        composeTestRule.waitForIdle()

        assertThat(manualEntryClicks).isEqualTo(1)
    }

    @Test
    fun showPermissionRationale_shouldDisplayRationaleDialogAndWireAllow() {
        var allowClicks = 0
        setViewfinderContent(
            uiState = QrScannerUiState(showPermissionRationale = true),
            onPermissionRationaleAllow = { allowClicks++ },
        )

        composeTestRule.onNodeWithText(
            context.getString(R.string.camera_permission_rationale_dialog_heading),
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.allow)).performClick()
        composeTestRule.waitForIdle()

        assertThat(allowClicks).isEqualTo(1)
    }

    @Test
    fun permanentlyDeniedRationale_shouldWireOpenSettingsInsteadOfAllow() {
        var openSettingsClicks = 0
        setViewfinderContent(
            uiState = QrScannerUiState(
                showPermissionRationale = true,
                isPermissionPermanentlyDenied = true,
            ),
            onPermissionRationaleOpenSettings = { openSettingsClicks++ },
        )

        composeTestRule.onNodeWithText(context.getString(R.string.open_settings)).performClick()
        composeTestRule.waitForIdle()

        assertThat(openSettingsClicks).isEqualTo(1)
    }

    @Test
    fun unsupportedQrError_shouldDisplayExplanationDialogAndWireDismiss() {
        var dismissClicks = 0
        setViewfinderContent(
            uiState = QrScannerUiState(unsupportedQrError = TotpUriError.UNSUPPORTED_ALGORITHM),
            onUnsupportedQrCodeDismiss = { dismissClicks++ },
        )

        composeTestRule.onNodeWithText(
            context.getString(R.string.unsupported_qr_code_dialog_heading),
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText(context.getString(R.string.common_ok)).performClick()
        composeTestRule.waitForIdle()

        assertThat(dismissClicks).isEqualTo(1)
    }

    @Test
    fun noDialogState_shouldDisplayNeitherRationaleNorUnsupportedDialog() {
        setViewfinderContent()

        composeTestRule.onNodeWithText(
            context.getString(R.string.camera_permission_rationale_dialog_heading),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithText(
            context.getString(R.string.unsupported_qr_code_dialog_heading),
        ).assertDoesNotExist()
    }

    private fun setViewfinderContent(
        uiState: QrScannerUiState = QrScannerUiState(),
        onClose: () -> Unit = {},
        onToggleTorch: () -> Unit = {},
        onEnterKeyManually: () -> Unit = {},
        onPermissionRationaleAllow: () -> Unit = {},
        onPermissionRationaleOpenSettings: () -> Unit = {},
        onPermissionRationaleCancel: () -> Unit = {},
        onPermissionRationaleDismiss: () -> Unit = {},
        onUnsupportedQrCodeDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SafeBoxTheme {
                QrScannerViewfinderContent(
                    uiState = uiState,
                    onClose = onClose,
                    onToggleTorch = onToggleTorch,
                    onEnterKeyManually = onEnterKeyManually,
                    onPermissionRationaleAllow = onPermissionRationaleAllow,
                    onPermissionRationaleOpenSettings = onPermissionRationaleOpenSettings,
                    onPermissionRationaleCancel = onPermissionRationaleCancel,
                    onPermissionRationaleDismiss = onPermissionRationaleDismiss,
                    onUnsupportedQrCodeDismiss = onUnsupportedQrCodeDismiss,
                )
            }
        }
    }
}
