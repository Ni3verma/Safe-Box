package com.andryoga.safebox.di

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

/**
 * Dagger Hilt module providing CameraX and ML Kit barcode scanning dependencies.
 */
@Module
@InstallIn(ViewModelComponent::class)
object CameraModule {

    /**
     * Provides the ML Kit [BarcodeScanner] configured for QR codes, scoped to the scanner
     * ViewModel so it can be closed in `onCleared` and its native detector released.
     *
     * A `@Singleton` scope would leak the detector for the lifetime of the process and make
     * closing it unsafe, because the next visit to the scanner would be handed a closed instance.
     */
    @Provides
    @ViewModelScoped
    fun provideBarcodeScanner(): BarcodeScanner {
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
}
