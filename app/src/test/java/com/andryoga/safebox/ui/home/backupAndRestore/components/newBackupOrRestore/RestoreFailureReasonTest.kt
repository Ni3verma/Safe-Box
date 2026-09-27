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

    /**
     * The ordinal is persisted in WorkManager output data and can outlive an app upgrade, so the
     * order is a compatibility contract. New entries must be appended, and this list extended.
     */
    @Test
    fun entries_shouldKeepPersistedOrdinalOrder() {
        assertThat(RestoreFailureReason.entries).containsExactly(
            RestoreFailureReason.INCORRECT_PASSWORD,
            RestoreFailureReason.CORRUPT_OR_INVALID_FILE,
            RestoreFailureReason.UNKNOWN_ERROR,
            RestoreFailureReason.BACKUP_TOO_NEW,
            RestoreFailureReason.BACKUP_EMPTY,
        ).inOrder()
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
