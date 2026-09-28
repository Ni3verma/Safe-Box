package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.work.Data
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupFailureReasonTest {

    @Test
    fun toWorkData_fromWorkData_roundTrip_shouldPreserveEnumValues() {
        BackupFailureReason.entries.forEach { reason ->
            assertThat(BackupFailureReason.fromWorkData(reason.toWorkData())).isEqualTo(reason)
        }
    }

    /**
     * The ordinal is persisted in WorkManager output data and can outlive an app upgrade, so the
     * order is a compatibility contract. New entries must be appended, and this list extended.
     */
    @Test
    fun entries_shouldKeepPersistedOrdinalOrder() {
        assertThat(BackupFailureReason.entries).containsExactly(
            BackupFailureReason.NOTHING_TO_BACKUP,
            BackupFailureReason.FOLDER_INACCESSIBLE,
            BackupFailureReason.WRITE_FAILED,
            BackupFailureReason.UNKNOWN,
        ).inOrder()
    }

    @Test
    fun fromWorkData_nullData_shouldReturnUnknown() {
        assertThat(BackupFailureReason.fromWorkData(null)).isEqualTo(BackupFailureReason.UNKNOWN)
    }

    /**
     * A worker that fails without output data (e.g. an uncaught crash) must not be misread as
     * [BackupFailureReason.NOTHING_TO_BACKUP], whose ordinal is 0.
     */
    @Test
    fun fromWorkData_emptyData_shouldReturnUnknown() {
        assertThat(BackupFailureReason.fromWorkData(Data.EMPTY))
            .isEqualTo(BackupFailureReason.UNKNOWN)
    }

    @Test
    fun fromWorkData_outOfRangeOrdinal_shouldReturnUnknown() {
        val data = Data.Builder()
            .putInt("key_backup_failure_reason", 999)
            .build()
        assertThat(BackupFailureReason.fromWorkData(data)).isEqualTo(BackupFailureReason.UNKNOWN)
    }
}
