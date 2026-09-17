package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.TotpUriError

/**
 * UI State for the QR code scanner screen.
 *
 * @property isTorchEnabled Whether camera flashlight/torch is active. Reconciled with the hardware
 * torch state, so it stays truthful after CameraX turns the torch off on a lifecycle stop.
 * @property hasFlashUnit Whether the bound camera actually has a flash unit. False until the
 * camera is bound, which also keeps the torch control hidden while permission is still pending.
 * @property showPermissionRationale Whether the camera permission rationale dialog is visible.
 * @property isCameraPermissionAskedBefore Whether camera permission has been requested previously,
 * or null if still loading from preferences.
 * @property isPermissionPermanentlyDenied Whether a permission request was denied by the system
 * without showing a prompt, meaning the rationale dialog must offer app settings instead of a
 * retry. Deliberately session scoped: a revoke or grant made in settings invalidates it.
 * @property unsupportedQrError Why the scanned `otpauth://` code was rejected, or null when no
 * such code has been scanned. Non-null also means scanning is paused until it is dismissed.
 */
data class QrScannerUiState(
    val isTorchEnabled: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val isCameraPermissionAskedBefore: Boolean? = null,
    val isPermissionPermanentlyDenied: Boolean = false,
    val unsupportedQrError: TotpUriError? = null,
)
