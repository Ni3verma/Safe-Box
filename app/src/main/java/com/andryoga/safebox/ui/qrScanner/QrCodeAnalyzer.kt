package com.andryoga.safebox.ui.qrScanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.engine.TotpUriParser
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.totp.models.TotpUriParseResult
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

/**
 * CameraX [ImageAnalysis.Analyzer] implementation using Google ML Kit Barcode Scanning
 * to detect and parse `otpauth://totp/...` QR codes from live camera frames.
 *
 * @param scanner Injected [BarcodeScanner] instance configured for QR code detection.
 * @param onQrCodeScanned Callback invoked when a valid TOTP QR code is detected and parsed.
 * @param onUnsupportedQrCode Callback invoked when an `otpauth://` QR code is detected but cannot
 * be used, so the screen can stop and explain why.
 */
class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onQrCodeScanned: (ParsedTotpData) -> Unit,
    private val onUnsupportedQrCode: (TotpUriError) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var isScanningActive = true

    // Note: CameraX ImageProxy.image is marked with @ExperimentalGetImage because it exposes the direct
    // underlying android.media.Image. Google's official CameraX + ML Kit guide requires opting in to this API.
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningActive) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (isScanningActive) {
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue.isNullOrBlank()) continue

                            when (val result = TotpUriParser.parse(rawValue)) {
                                is TotpUriParseResult.Success -> {
                                    isScanningActive = false
                                    onQrCodeScanned(result.data)
                                    return@addOnSuccessListener
                                }

                                is TotpUriParseResult.Unsupported -> {
                                    Timber.i("Unusable otpauth QR code scanned: %s", result.reason)
                                    isScanningActive = false
                                    onUnsupportedQrCode(result.reason)
                                    return@addOnSuccessListener
                                }

                                TotpUriParseResult.NotTotpUri -> {
                                    Timber.d("Non-TOTP QR code in frame, continuing to scan")
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { error ->
                    Timber.e(error, "Barcode scanning failed")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Timber.e(e, "Failed to process image frame")
            imageProxy.close()
        }
    }

    /**
     * Re-arms the analyzer after a detection so the next frames are inspected again.
     *
     * Called when the user dismisses the unsupported QR code message.
     */
    fun reset() {
        isScanningActive = true
    }
}
