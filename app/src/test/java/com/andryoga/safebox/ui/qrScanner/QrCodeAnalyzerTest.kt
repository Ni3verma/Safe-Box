package com.andryoga.safebox.ui.qrScanner

import android.util.Log
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class QrCodeAnalyzerTest {

    private var scannedTotpData: ParsedTotpData? = null
    private var unsupportedReason: TotpUriError? = null
    private val decoder = RecordingDecoder()
    private lateinit var analyzer: QrCodeAnalyzer

    @Before
    fun setUp() {
        analyzer = QrCodeAnalyzer(
            decoder = decoder,
            onQrCodeScanned = { data -> scannedTotpData = data },
            onUnsupportedQrCode = { reason -> unsupportedReason = reason },
        )
    }

    @Test
    fun analyze_whenFrameHasNoQrCode_closesImageProxyAndKeepsScanning() {
        decoder.nextResult = { null }

        val imageProxy = analyzeFrame()
        analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        assertThat(decoder.decodedFrames).isEqualTo(2)
        assertThat(scannedTotpData).isNull()
        assertThat(unsupportedReason).isNull()
    }

    @Test
    fun analyze_whenDecodedTextIsBlank_keepsScanningWithoutParsing() {
        decoder.nextResult = { "   " }

        analyzeFrame()
        analyzeFrame()

        assertThat(decoder.decodedFrames).isEqualTo(2)
        assertThat(scannedTotpData).isNull()
        assertThat(unsupportedReason).isNull()
    }

    @Test
    fun analyze_whenDecoderThrows_closesImageProxyAndDoesNotInvokeCallback() {
        decoder.nextResult = { throw IllegalStateException("bad buffer layout") }

        val imageProxy = analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNull()
        assertThat(unsupportedReason).isNull()
    }

    @Test
    fun analyze_whenDecoderKeepsThrowing_reportsTheErrorOnceAndKeepsScanning() {
        val reportedErrors = mutableListOf<Throwable?>()
        val tree = object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (priority == Log.ERROR) reportedErrors += t
            }
        }
        Timber.plant(tree)
        try {
            decoder.nextResult = { throw IllegalStateException("bad buffer layout") }

            repeat(3) { analyzeFrame() }

            assertThat(decoder.decodedFrames).isEqualTo(3)
            assertThat(reportedErrors).hasSize(1)
            assertThat(reportedErrors.single()).isInstanceOf(IllegalStateException::class.java)
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun analyze_whenTotpQrCodeDecoded_invokesCallbackAndStopsDecodingSubsequentFrames() {
        decoder.nextResult = {
            "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test"
        }

        val imageProxy = analyzeFrame()
        val secondImageProxy = analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        verify(exactly = 1) { secondImageProxy.close() }
        assertThat(decoder.decodedFrames).isEqualTo(1)
        assertThat(scannedTotpData?.config?.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(unsupportedReason).isNull()
    }

    @Test
    fun analyze_whenUnusableOtpauthCodeDecoded_reportsReasonAndStopsScanning() {
        decoder.nextResult = {
            "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&algorithm=SHA3"
        }

        val imageProxy = analyzeFrame()
        analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        assertThat(decoder.decodedFrames).isEqualTo(1)
        assertThat(unsupportedReason).isEqualTo(TotpUriError.UNSUPPORTED_ALGORITHM)
        assertThat(scannedTotpData).isNull()
    }

    @Test
    fun analyze_whenMalformedOtpauthCodeDecoded_reportsReasonAndStopsScanning() {
        decoder.nextResult = { "otpauth://totp/ACME^Co:user@example.com?secret=JBSWY3DPEHPK3PXP" }

        val imageProxy = analyzeFrame()
        analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        assertThat(decoder.decodedFrames).isEqualTo(1)
        assertThat(unsupportedReason).isEqualTo(TotpUriError.MALFORMED_URI)
        assertThat(scannedTotpData).isNull()
    }

    @Test
    fun analyze_whenUnrelatedQrCodeDecoded_keepsScanningWithoutReportingAnError() {
        decoder.nextResult = { "https://example.com/not-an-otp-code" }

        val imageProxy = analyzeFrame()
        analyzeFrame()

        verify(exactly = 1) { imageProxy.close() }
        assertThat(decoder.decodedFrames).isEqualTo(2)
        assertThat(scannedTotpData).isNull()
        assertThat(unsupportedReason).isNull()
    }

    @Test
    fun reset_reArmsTheAnalyzerAfterADetection() {
        decoder.nextResult = {
            "otpauth://totp/First:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=First"
        }
        analyzeFrame()
        assertThat(scannedTotpData?.title).isEqualTo("First - user@example.com")

        // Frames are ignored until reset() is called.
        decoder.nextResult = {
            "otpauth://totp/Second:user@example.com?secret=MZXW6YTB&issuer=Second"
        }
        analyzeFrame()
        assertThat(decoder.decodedFrames).isEqualTo(1)
        assertThat(scannedTotpData?.title).isEqualTo("First - user@example.com")

        analyzer.reset()
        analyzeFrame()

        assertThat(decoder.decodedFrames).isEqualTo(2)
        assertThat(scannedTotpData?.title).isEqualTo("Second - user@example.com")
        assertThat(scannedTotpData?.config?.secretKey).isEqualTo("MZXW6YTB")
    }

    /**
     * Drives one frame through the analyzer.
     *
     * @return The image proxy that was analyzed, so callers can verify it was closed.
     */
    private fun analyzeFrame(): ImageProxy {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(imageProxy)
        return imageProxy
    }

    /**
     * Fake decoder that returns whatever [nextResult] produces and counts the frames it was asked
     * to decode, so tests can assert that a disarmed analyzer never decodes.
     */
    private class RecordingDecoder : QrCodeDecoder {
        var nextResult: () -> String? = { null }
        var decodedFrames = 0
            private set

        override fun decode(imageProxy: ImageProxy): String? {
            decodedFrames++
            return nextResult()
        }
    }
}
