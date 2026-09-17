package com.andryoga.safebox.ui.qrScanner

import android.media.Image
import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.models.ParsedTotpData
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
import org.junit.Before
import org.junit.Test

class QrCodeAnalyzerTest {

    private var scannedTotpData: ParsedTotpData? = null
    private lateinit var mockBarcodeScanner: BarcodeScanner
    private lateinit var analyzer: QrCodeAnalyzer

    @Before
    fun setUp() {
        scannedTotpData = null
        mockBarcodeScanner = mockk(relaxed = true)
        analyzer = QrCodeAnalyzer(
            scanner = mockBarcodeScanner,
            onQrCodeScanned = { data -> scannedTotpData = data },
        )
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
        mockkStatic(InputImage::class)
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

        unmockkStatic(InputImage::class)
    }

    @Test
    fun analyze_whenBarcodeDetected_invokesCallbackAndDisablesScanningForSubsequentFrames() {
        mockkStatic(InputImage::class)
        val imageProxy = mockk<ImageProxy>(relaxed = true)
        val mockMediaImage = mockk<Image>(relaxed = true)
        val mockInputImage = mockk<InputImage>()
        val mockBarcode = mockk<Barcode>(relaxed = true) {
            every { rawValue } returns "otpauth://totp/Test:user@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Test"
        }

        every { imageProxy.image } returns mockMediaImage
        every { InputImage.fromMediaImage(mockMediaImage, any()) } returns mockInputImage
        val mockTask = mockk<Task<List<Barcode>>>(relaxed = true)
        every { mockBarcodeScanner.process(mockInputImage) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<List<Barcode>>>()
            listener.onSuccess(listOf(mockBarcode))
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockTask.addOnCompleteListener(any()) } answers {
            val listener = firstArg<OnCompleteListener<List<Barcode>>>()
            listener.onComplete(mockTask)
            mockTask
        }

        analyzer.analyze(imageProxy)

        verify(exactly = 1) { imageProxy.close() }
        assertThat(scannedTotpData).isNotNull()
        assertThat(scannedTotpData?.config?.secretKey).isEqualTo("JBSWY3DPEHPK3PXP")

        val secondImageProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(secondImageProxy)

        verify(exactly = 1) { secondImageProxy.close() }
        verify(exactly = 0) { secondImageProxy.image }

        unmockkStatic(InputImage::class)
    }
}
