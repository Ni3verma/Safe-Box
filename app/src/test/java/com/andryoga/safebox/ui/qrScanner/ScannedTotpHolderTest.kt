package com.andryoga.safebox.ui.qrScanner

import com.andryoga.safebox.totp.models.ParsedTotpData
import com.andryoga.safebox.totp.models.TotpAlgorithm
import com.andryoga.safebox.totp.models.TotpConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ScannedTotpHolderTest {

    private lateinit var holder: ScannedTotpHolder

    private val scannedData = ParsedTotpData(
        title = "Google - alex@gmail.com",
        config = TotpConfig(
            secretKey = "JBSWY3DPEHPK3PXP",
            algorithm = TotpAlgorithm.SHA256,
            digits = 8,
            period = 60,
        ),
    )

    @Before
    fun setUp() {
        holder = ScannedTotpHolder()
    }

    @Test
    fun consumeWithoutScan_shouldReturnNull() {
        assertThat(holder.consume()).isNull()
    }

    @Test
    fun consumeAfterPut_shouldReturnScannedData() {
        holder.put(scannedData)

        assertThat(holder.consume()).isEqualTo(scannedData)
    }

    @Test
    fun secondConsume_shouldReturnNullSoALaterManualEntryIsNotPrefilled() {
        holder.put(scannedData)
        holder.consume()

        assertThat(holder.consume()).isNull()
    }

    @Test
    fun putTwiceWithoutConsuming_shouldKeepOnlyTheLatestScan() {
        val abandonedScan = scannedData.copy(title = "Abandoned")
        holder.put(abandonedScan)
        holder.put(scannedData)

        assertThat(holder.consume()).isEqualTo(scannedData)
    }
}
