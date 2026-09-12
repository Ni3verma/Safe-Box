package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.ParsedTotpData

/**
 * Actions dispatched from [QrScannerScreen] to [QrScannerViewModel].
 */
sealed interface QrScannerScreenAction {
    object OnScannerVisible : QrScannerScreenAction
    object OnToggleTorch : QrScannerScreenAction
    class OnQrCodeScanned(val totpData: ParsedTotpData) : QrScannerScreenAction
    object OnEnterKeyManuallyClicked : QrScannerScreenAction
    object OnCloseClicked : QrScannerScreenAction
    object OnShowPermissionRationale : QrScannerScreenAction
    object OnPermissionRationaleDismissed : QrScannerScreenAction
    class OnPermissionRationaleAllowClicked(val isRedirectingToSettings: Boolean) :
        QrScannerScreenAction

    object OnPermissionRationaleCancelClicked : QrScannerScreenAction
    object OnInitialCameraPermissionRequested : QrScannerScreenAction
}
