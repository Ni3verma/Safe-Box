package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.ParsedTotpData
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

/**
 * In-memory, single-consumption hand-off of the data parsed from a scanned QR code, written by
 * [QrScannerViewModel] and read once by the create-record layout.
 *
 * The scanned payload contains the plaintext Base32 seed, so it deliberately never travels as a
 * navigation argument: route arguments are serialised into the destination's `Bundle`, which
 * `onSaveInstanceState` writes to unencrypted system storage outside the vault. Holding the seed
 * in memory instead means it survives configuration change but not process death, where the
 * correct recovery is for the user to rescan.
 *
 * Scoped to the retained activity rather than the application so the value cannot outlive the
 * screen that produced it. Both the write and the read happen on the main thread, so no
 * synchronisation is needed.
 */
@ActivityRetainedScoped
class ScannedTotpHolder @Inject constructor() {
    private var pendingData: ParsedTotpData? = null

    /**
     * Stores the scanned data, overwriting anything an abandoned scan left behind.
     *
     * @param data Parsed payload of the QR code the user just scanned.
     */
    fun put(data: ParsedTotpData) {
        pendingData = data
    }

    /**
     * Returns the pending scanned data and clears it, so a later create-record screen opened by
     * hand is never silently prefilled from an earlier scan.
     *
     * @return The pending data, or null when the user reached the create screen without scanning.
     */
    fun consume(): ParsedTotpData? {
        val data = pendingData
        pendingData = null
        return data
    }
}
