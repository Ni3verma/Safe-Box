package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.TotpUriError

/**
 * UI State for the QR code scanner screen.
 *
 * @property isTorchEnabled Whether camera flashlight/torch is active.
 * @property showPermissionRationale Whether the camera permission rationale dialog is visible.
 * @property isCameraPermissionAskedBefore Whether camera permission has been requested previously,
 * or null if still loading from preferences.
 * @property unsupportedQrError Why the scanned `otpauth://` code was rejected, or null when no
 * such code has been scanned. Non-null also means scanning is paused until it is dismissed.
 */
data class QrScannerUiState(
    val isTorchEnabled: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val isCameraPermissionAskedBefore: Boolean? = null,
    val unsupportedQrError: TotpUriError? = null,
)
