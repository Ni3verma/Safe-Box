package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.work.Data
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RestoreFailureReasonTest {

    @Test
    fun toWorkData_fromWorkData_roundTrip_shouldPreserveEnumValues() {
        RestoreFailureReason.entries.forEach { reason ->
            val workData = reason.toWorkData()
            val parsedReason = RestoreFailureReason.fromWorkData(workData)
            assertThat(parsedReason).isEqualTo(reason)
        }
    }

    @Test
    fun fromWorkData_nullData_shouldReturnUnknownError() {
        val result = RestoreFailureReason.fromWorkData(null)
        assertThat(result).isEqualTo(RestoreFailureReason.UNKNOWN_ERROR)
    }

    @Test
    fun fromWorkData_emptyData_shouldReturnUnknownError() {
        val result = RestoreFailureReason.fromWorkData(Data.EMPTY)
        assertThat(result).isEqualTo(RestoreFailureReason.UNKNOWN_ERROR)
    }

    @Test
    fun fromWorkData_outOfRangeOrdinal_shouldReturnUnknownError() {
        val data = Data.Builder()
            .putInt("key_restore_failure_reason", 999)
            .build()
        val result = RestoreFailureReason.fromWorkData(data)
        assertThat(result).isEqualTo(RestoreFailureReason.UNKNOWN_ERROR)
    }
}
