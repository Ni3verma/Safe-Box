package com.andryoga.safebox.ui.qrScanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Extracts the text payload of a QR code from a single camera frame.
 *
 * This is the seam between the frame plumbing in [QrCodeAnalyzer] and the decoding library, so the
 * analyzer's TOTP dispatch logic can be unit tested with a canned payload and the library can be
 * swapped without touching the camera pipeline.
 *
 * Implementations are called on the [ImageAnalysis] executor for every frame, so they must be
 * cheap to invoke and must not touch UI state. The caller owns the [ImageProxy] and closes it
 * after `decode` returns; implementations must not close it themselves.
 */
fun interface QrCodeDecoder {

    /**
     * @param imageProxy A frame from [ImageAnalysis]; valid for the duration of this call only.
     * @return The decoded QR code text, or null when the frame holds no decodable QR code.
     */
    fun decode(imageProxy: ImageProxy): String?
}
