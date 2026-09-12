package com.andryoga.safebox.di

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module providing CameraX and ML Kit barcode scanning dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    /**
     * Provides a singleton instance of Google ML Kit [BarcodeScanner] configured for QR codes.
     */
    @Provides
    @Singleton
    fun provideBarcodeScanner(): BarcodeScanner {
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
}
