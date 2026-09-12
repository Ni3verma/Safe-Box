package com.andryoga.safebox.ui.qrScanner

/**
 * UI State for the QR code scanner screen.
 *
 * @property isTorchEnabled Whether camera flashlight/torch is active.
 * @property showPermissionRationale Whether the camera permission rationale dialog is visible.
 * @property isCameraPermissionAskedBefore Whether camera permission has been requested previously,
 * or null if still loading from preferences.
 */
data class QrScannerUiState(
    val isTorchEnabled: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val isCameraPermissionAskedBefore: Boolean? = null,
)
