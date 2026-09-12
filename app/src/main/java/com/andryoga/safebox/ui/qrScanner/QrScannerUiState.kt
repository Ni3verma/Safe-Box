package com.andryoga.safebox.ui.qrScanner

/**
 * UI State for the QR code scanner screen.
 *
 * @property isTorchEnabled Whether camera flashlight/torch is active.
 * @property showPermissionRationale Whether the camera permission rationale dialog is visible.
 */
data class QrScannerUiState(
    val isTorchEnabled: Boolean = false,
    val showPermissionRationale: Boolean = false,
)
