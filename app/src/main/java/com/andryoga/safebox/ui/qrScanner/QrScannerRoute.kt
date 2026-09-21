package com.andryoga.safebox.ui.qrScanner

import kotlinx.serialization.Serializable

/**
 * Navigation destination for the full screen QR scanner.
 *
 * Carries no arguments in either direction. The scanned payload is handed to the create-record
 * screen through [ScannedTotpHolder] so the seed never enters the saved instance state.
 */
@Serializable
data object QrScannerRoute
