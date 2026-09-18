package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError

/**
 * Actions dispatched from [QrScannerScreen] to [QrScannerViewModel].
 */
sealed interface QrScannerScreenAction {
    object OnScannerVisible : QrScannerScreenAction
    object OnToggleTorch : QrScannerScreenAction
    class OnCameraBound(val hasFlashUnit: Boolean) : QrScannerScreenAction
    class OnTorchStateChanged(val isEnabled: Boolean) : QrScannerScreenAction
    class OnQrCodeScanned(val totpData: ParsedTotpData) : QrScannerScreenAction
    class OnUnsupportedQrCodeScanned(val reason: TotpUriError) : QrScannerScreenAction
    object OnUnsupportedQrCodeDismissed : QrScannerScreenAction
    object OnEnterKeyManuallyClicked : QrScannerScreenAction
    object OnCloseClicked : QrScannerScreenAction
    object OnShowPermissionRationale : QrScannerScreenAction
    object OnPermissionRationaleDismissed : QrScannerScreenAction
    object OnPermissionRationaleAllowClicked : QrScannerScreenAction
    object OnPermissionRationaleCancelClicked : QrScannerScreenAction
    class OnCameraPermissionResult(val isGranted: Boolean, val canAskAgain: Boolean) :
        QrScannerScreenAction
    object OnOpenAppSettingsClicked : QrScannerScreenAction
}
