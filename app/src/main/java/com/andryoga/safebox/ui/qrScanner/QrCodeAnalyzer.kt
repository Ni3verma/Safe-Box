package com.andryoga.safebox.ui.qrScanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.engine.TotpUriParser
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError
import com.andryoga.safebox.totp.models.TotpUriParseResult
import timber.log.Timber

/**
 * CameraX [ImageAnalysis.Analyzer] that runs every frame through a [QrCodeDecoder] and parses any
 * `otpauth://totp/...` payload it finds.
 *
 * Decoding is synchronous, so both callbacks fire on the analysis executor's thread. Callers that
 * need the main thread, such as anything that navigates, must post to it themselves.
 *
 * @param decoder Extracts the QR code text from a frame.
 * @param onQrCodeScanned Callback invoked when a valid TOTP QR code is detected and parsed.
 * @param onUnsupportedQrCode Callback invoked when an `otpauth://` QR code is detected but cannot
 * be used, so the screen can stop and explain why.
 */
class QrCodeAnalyzer(
    private val decoder: QrCodeDecoder,
    private val onQrCodeScanned: (ParsedTotpData) -> Unit,
    private val onUnsupportedQrCode: (TotpUriError) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var isScanningActive = true

    /**
     * A decoder failure is almost always systematic, such as an unexpected buffer layout on one
     * device, and would otherwise be reported once per frame. The release log tree forwards every
     * error to Crashlytics, so only the first occurrence is reported.
     */
    private var hasReportedDecodeFailure = false

    override fun analyze(imageProxy: ImageProxy) {
        if (!isScanningActive) {
            imageProxy.close()
            return
        }

        val rawValue = try {
            decoder.decode(imageProxy)
        } catch (e: Exception) {
            if (!hasReportedDecodeFailure) {
                hasReportedDecodeFailure = true
                Timber.e(e, "Failed to decode camera frame")
            }
            null
        } finally {
            imageProxy.close()
        }

        if (!rawValue.isNullOrBlank()) {
            handlePayload(rawValue)
        }
    }

    private fun handlePayload(rawValue: String) {
        when (val result = TotpUriParser.parse(rawValue)) {
            is TotpUriParseResult.Success -> {
                isScanningActive = false
                onQrCodeScanned(result.data)
            }

            is TotpUriParseResult.Unsupported -> {
                Timber.i("Unusable otpauth QR code scanned: %s", result.reason)
                isScanningActive = false
                onUnsupportedQrCode(result.reason)
            }

            TotpUriParseResult.NotTotpUri -> {
                Timber.d("Non-TOTP QR code in frame, continuing to scan")
            }
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
