package com.andryoga.safebox.ui.qrScanner

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.engine.TotpUriParser
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

/**
 * CameraX [ImageAnalysis.Analyzer] implementation using Google ML Kit Barcode Scanning
 * to detect and parse `otpauth://totp/...` QR codes from live camera frames.
 *
 * @param scanner Injected [BarcodeScanner] instance configured for QR code detection.
 * @param onQrCodeScanned Callback invoked when a valid TOTP QR code is detected and parsed.
 * @param onScanFailed Optional callback invoked when ML Kit encounters a frame processing error.
 */
class QrCodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onQrCodeScanned: (ParsedTotpData) -> Unit,
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
                            if (!rawValue.isNullOrBlank()) {
                                try {
                                    val parsedData = TotpUriParser.parse(rawValue)
                                    isScanningActive = false
                                    onQrCodeScanned(parsedData)
                                    break
                                } catch (e: Exception) {
                                    Timber.d("Non-TOTP or malformed QR code scanned: %s", e.message)
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
     * Resets the analyzer to allow scanning new QR codes after a previous detection.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun reset() {
        isScanningActive = true
    }
}
