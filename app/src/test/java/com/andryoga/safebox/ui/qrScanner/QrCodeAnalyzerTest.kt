package com.andryoga.safebox.ui.qrScanner

import androidx.camera.core.ImageProxy
import com.andryoga.safebox.totp.models.ParsedTotpData
import com.google.common.truth.Truth.assertThat
import com.google.mlkit.vision.barcode.BarcodeScanner
import io.mockk.every
import io.mockk.mockk
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
}
