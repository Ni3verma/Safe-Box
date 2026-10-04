package com.andryoga.safebox.di

import com.andryoga.safebox.ui.qrScanner.QrCodeDecoder
import com.andryoga.safebox.ui.qrScanner.ZxingQrCodeDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Dagger Hilt module providing the QR code decoding dependency used by the camera scanner.
 */
@Module
@InstallIn(ViewModelComponent::class)
abstract class CameraModule {

    /**
     * Binds [ZxingQrCodeDecoder] to the [QrCodeDecoder] interface.
     *
     * Unscoped on purpose: the decoder is stateless and cheap, so there is nothing to share or to
     * release when the scanner ViewModel is cleared.
     *
     * @param zxingQrCodeDecoder Concrete ZXing-backed decoder.
     * @return [QrCodeDecoder] interface for injection into the scanner ViewModel.
     */
    @Binds
    abstract fun bindQrCodeDecoder(
        zxingQrCodeDecoder: ZxingQrCodeDecoder,
    ): QrCodeDecoder
}
