package com.andryoga.safebox.ui.qrScanner

import android.media.Image
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpUriError
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class QrCodeAnalyzerTest {

    private var scannedTotpData: ParsedTotpData? = null
    private var unsupportedReason: TotpUriError? = null
    private lateinit var mockBarcodeScanner: BarcodeScanner
    private lateinit var analyzer: QrCodeAnalyzer

    @Before
    fun setUp() {
        mockkStatic(InputImage::class)
        scannedTotpData = null
        unsupportedReason = null
        mockBarcodeScanner = mockk(relaxed = true)
        analyzer = QrCodeAnalyzer(
            scanner = mockBarcodeScanner,
            onQrCodeScanned = { data -> scannedTotpData = data },
            onUnsupportedQrCode = { reason -> unsupportedReason = reason },
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(InputImage::class)
    }

    @Test
    fun analyze_whenMediaImageIsNull_closesImageProxyAndDoesNotInvokeCallback() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        every { imageProxy.image } returns null

        analyzer.analyze(imageProxy)

        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNull()
    }

    @Test
    fun reset_resetsScanningActiveState() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        every { imageProxy.image } returns null

        analyzer.analyze(imageProxy)
        analyzer.reset()

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        every { secondImageProxy.image } returns null
        analyzer.analyze(secondImageProxy)

        verify(exactly = 1) { secondImageProxy.close() }
        assertThat(scannedTotpData).isNull()
    }

    @Test
    fun analyze_whenScannerProcessThrows_closesImageProxyAndDoesNotInvokeCallback() {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val mockMediaImage = mockk<Image>(relaxed = true)
        val mockInputImage = mockk<InputImage>()

        every { imageProxy.image } returns mockMediaImage
        every { InputImage.fromMediaImage(mockMediaImage, any()) } returns mockInputImage
        every { mockBarcodeScanner.process(mockInputImage) } throws RuntimeException("ML Kit internal error")

        analyzer.analyze(imageProxy)

        verify(exactly = 1) { mockBarcodeScanner.process(mockInputImage) }
        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNull()
    }

    @Test
    fun analyze_whenBarcodeDetected_invokesCallbackAndDisablesScanningForSubsequentFrames() {
        val imageProxy = analyzeFrameContaining(
            "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test",
        )

        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNotNull()
        assertThat(scannedTotpData?.config?.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")
        assertThat(unsupportedReason).isNull()

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(secondImageProxy)

        verify(exactly = 1) { secondImageProxy.close() }
        verify(exactly = 0) { secondImageProxy.image }
    }

    @Test
    fun analyze_whenUnusableOtpauthCodeDetected_reportsReasonAndStopsScanning() {
        val imageProxy = analyzeFrameContaining(
            "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&algorithm=SHA3",
        )

        verify(exactly = 1) { imageProxy.close() }
        assertThat(unsupportedReason).isEqualTo(TotpUriError.UNSUPPORTED_ALGORITHM)
        assertThat(scannedTotpData).isNull()

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(secondImageProxy)

        verify(exactly = 0) { secondImageProxy.image }
    }

    @Test
    fun analyze_whenMalformedOtpauthCodeDetected_reportsReasonAndStopsScanning() {
        val imageProxy = analyzeFrameContaining(
            "otpauth://totp/ACME^Co:user@example.com?secret=JBSWY3DPEHPK3PXP",
        )

        verify(exactly = 1) { imageProxy.close() }
        assertThat(unsupportedReason).isEqualTo(TotpUriError.MALFORMED_URI)
        assertThat(scannedTotpData).isNull()

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(secondImageProxy)

        verify(exactly = 0) { secondImageProxy.image }
    }

    @Test
    fun analyze_whenUnrelatedQrCodeDetected_keepsScanningWithoutReportingAnError() {
        val imageProxy = analyzeFrameContaining("https://example.com/not-an-otp-code")

        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNull()
        assertThat(unsupportedReason).isNull()

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        every { secondImageProxy.image } returns null
        analyzer.analyze(secondImageProxy)

        verify(exactly = 1) { secondImageProxy.image }
    }

    /**
     * Drives one frame through the analyzer with a single barcode carrying [qrPayload], running
     * the ML Kit success and completion listeners synchronously.
     *
     * @param qrPayload Raw value the mocked barcode reports.
     * @return The image proxy that was analyzed, so callers can verify it was closed.
     */
    private fun analyzeFrameContaining(qrPayload: String): ImageProxy {
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val mockMediaImage = mockk<Image>(relaxed = true)
        val mockInputImage = mockk<InputImage>()
        val mockBarcode = mockk<Barcode>(relaxed = true) {
            every { rawValue } returns qrPayload
        }
        val mockTask = mockk<Task<List<Barcode>>>(relaxed = true)

        every { imageProxy.image } returns mockMediaImage
        every { InputImage.fromMediaImage(mockMediaImage, any()) } returns mockInputImage
        every { mockBarcodeScanner.process(mockInputImage) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<List<Barcode>>>().onSuccess(listOf(mockBarcode))
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockTask.addOnCompleteListener(any()) } answers {
            firstArg<OnCompleteListener<List<Barcode>>>().onComplete(mockTask)
            mockTask
        }

        analyzer.analyze(imageProxy)
        return imageProxy
    }
}
