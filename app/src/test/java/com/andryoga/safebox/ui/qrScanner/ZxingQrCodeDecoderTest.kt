package com.andryoga.safebox.ui.qrScanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.nio.ByteBuffer

/**
 * Round-trips real QR codes through [ZxingQrCodeDecoder] on the JVM.
 *
 * Frames are synthesised to look like what CameraX hands the analyzer: an 8-bit luminance plane,
 * one byte per pixel, with rows optionally padded to a larger stride. The padding cases are the
 * ones that matter, since a decoder that assumes `rowStride == width` decodes nothing on devices
 * that pad.
 */
class ZxingQrCodeDecoderTest {

    private val decoder = ZxingQrCodeDecoder()

    @Test
    fun decode_whenFrameContainsQrCode_returnsItsText() {
        val frame = qrFrame(TOTP_URI)

        assertThat(decoder.decode(frame)).isEqualTo(TOTP_URI)
    }

    @Test
    fun decode_whenRowsArePadded_honoursRowStride() {
        val frame = qrFrame(TOTP_URI, rowPadding = 64)

        assertThat(decoder.decode(frame)).isEqualTo(TOTP_URI)
    }

    @Test
    fun decode_whenBufferEndsAfterLastPixel_stillDecodes() {
        // Camera2 may end the Y buffer right after the last pixel of the last row rather than
        // after that row's padding, so the buffer is shorter than rowStride * height.
        val frame = qrFrame(TOTP_URI, rowPadding = 64, trimLastRowPadding = true)

        assertThat(decoder.decode(frame)).isEqualTo(TOTP_URI)
    }

    @Test
    fun decode_whenQrCodeIsRotated_returnsItsTextWithoutRotationMetadata() {
        val frame = qrFrame(TOTP_URI, quarterTurns = 1)

        assertThat(decoder.decode(frame)).isEqualTo(TOTP_URI)
    }

    @Test
    fun decode_whenFrameIsBlank_returnsNull() {
        val frame = frameOf(ByteArray(SIZE * SIZE) { WHITE }, frameWidth = SIZE, frameHeight = SIZE, stride = SIZE)

        assertThat(decoder.decode(frame)).isNull()
    }

    @Test
    fun decode_whenFrameIsNotYuv420_returnsNullWithoutReadingPlanes() {
        // Strict mock: touching anything but format fails the test.
        val frame = mockk<ImageProxy>()
        every { frame.format } returns ImageFormat.JPEG

        assertThat(decoder.decode(frame)).isNull()
    }

    /**
     * Renders [text] as a QR code into a synthetic luminance plane.
     *
     * @param rowPadding Extra bytes appended to every row, so `rowStride = width + rowPadding`.
     * @param trimLastRowPadding Drop the padding after the final row, as Camera2 buffers may.
     * @param quarterTurns Clockwise quarter turns applied to the rendered code.
     */
    private fun qrFrame(
        text: String,
        rowPadding: Int = 0,
        trimLastRowPadding: Boolean = false,
        quarterTurns: Int = 0,
    ): ImageProxy {
        var matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE)
        repeat(quarterTurns) { matrix = matrix.rotatedClockwise() }
        val width = matrix.width
        val height = matrix.height
        val stride = width + rowPadding
        val length = if (trimLastRowPadding) stride * (height - 1) + width else stride * height
        val luminance = ByteArray(length) { WHITE }
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (matrix.get(x, y)) luminance[y * stride + x] = BLACK
            }
        }
        return frameOf(luminance, frameWidth = width, frameHeight = height, stride = stride)
    }

    private fun frameOf(luminance: ByteArray, frameWidth: Int, frameHeight: Int, stride: Int): ImageProxy {
        val plane = mockk<ImageProxy.PlaneProxy>()
        every { plane.buffer } returns ByteBuffer.wrap(luminance)
        every { plane.rowStride } returns stride
        every { plane.pixelStride } returns 1
        val frame = mockk<ImageProxy>()
        every { frame.format } returns ImageFormat.YUV_420_888
        every { frame.width } returns frameWidth
        every { frame.height } returns frameHeight
        every { frame.planes } returns arrayOf(plane)
        return frame
    }

    private fun BitMatrix.rotatedClockwise(): BitMatrix {
        val rotated = BitMatrix(height, width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (get(x, y)) rotated.set(height - 1 - y, x)
            }
        }
        return rotated
    }

    private companion object {
        const val TOTP_URI =
            "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example&digits=6&period=30"

        /** Large enough for roughly five pixels per module once the quiet zone is included. */
        const val SIZE = 240
        const val BLACK: Byte = 0

        /** 0xFF as a signed byte. */
        const val WHITE: Byte = -1
    }
}
