package com.andryoga.safebox.ui.qrScanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import javax.inject.Inject

/**
 * [QrCodeDecoder] backed by ZXing's pure-Java [QRCodeReader].
 *
 * Chosen over ML Kit's bundled barcode model because it ships no native library and no TFLite
 * model: the bundled model alone added ~2.7 MB to every device's download (ADR-0005). It also
 * needs no Google Play services, which keeps the scanner working on de-Googled devices and on the
 * `aosp-atd` emulator image the UI tests run on.
 *
 * Only the luminance (Y) plane of the `YUV_420_888` frame is read; QR codes are monochrome, so the
 * chroma planes carry nothing useful. Frame rotation is deliberately ignored: a QR code's three
 * finder patterns let the detector recover its orientation, so decoding works at any angle without
 * rotating the pixels first.
 *
 * Stateless, so one instance may be shared and called from any thread. The [QRCodeReader] is
 * created per call because ZXing does not document its readers as thread-safe, and the object is
 * trivially cheap next to the frame copy.
 */
class ZxingQrCodeDecoder @Inject constructor() : QrCodeDecoder {

    override fun decode(imageProxy: ImageProxy): String? {
        // ImageAnalysis emits YUV_420_888 unless configured otherwise. For any other format plane 0
        // is not luminance, and reading it as such would silently decode garbage.
        if (imageProxy.format != ImageFormat.YUV_420_888) return null

        val yPlane = imageProxy.planes[0]
        val buffer = yPlane.buffer
        buffer.rewind()
        val luminance = ByteArray(buffer.remaining())
        buffer.get(luminance)

        // The Y plane is guaranteed a pixel stride of 1, but its row stride may exceed the width
        // when rows are padded, so the stride is passed as the data width and the real width as
        // the crop width. ZXing never reads past the end of the final row, which matters because
        // the buffer is allowed to be shorter than rowStride * height.
        val source = PlanarYUVLuminanceSource(
            luminance,
            yPlane.rowStride,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false,
        )
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), DECODE_HINTS).text
        } catch (e: ReaderException) {
            // NotFound, Checksum and Format exceptions all mean "nothing usable in this frame".
            null
        }
    }

    private companion object {
        /**
         * `TRY_HARDER` narrows the finder-pattern row skip, which helps small or distant codes at
         * a modest CPU cost that does not matter on the single analysis thread.
         */
        val DECODE_HINTS: Map<DecodeHintType, Any> = mapOf(DecodeHintType.TRY_HARDER to true)
    }
}
