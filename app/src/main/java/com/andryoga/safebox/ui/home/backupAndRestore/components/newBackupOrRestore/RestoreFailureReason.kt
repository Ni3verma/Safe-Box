package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.work.Data
import androidx.work.workDataOf

/**
 * Type-safe failure reasons emitted by [com.andryoga.safebox.worker.RestoreDataWorker]
 * in output data to communicate failure causes to the UI layer.
 *
 * **Append new entries at the end only.** [toWorkData] serializes the [ordinal], and that value
 * crosses the WorkManager `Data` boundary, where it can outlive an app upgrade while the work is
 * still enqueued. Reordering or inserting would silently remap an in-flight failure reason.
 * `RestoreFailureReasonTest` pins every ordinal to enforce this.
 */
enum class RestoreFailureReason {
    // Decryption failed due to an incorrect master password.
    INCORRECT_PASSWORD,

    // Backup file could not be read, parsed, or contains corrupted/invalid data structure.
    CORRUPT_OR_INVALID_FILE,

    // An unclassified or unexpected error occurred during the restore operation.
    UNKNOWN_ERROR,

    // Backup was written by a newer app build whose backup format this build does not support.
    BACKUP_TOO_NEW;

    fun toWorkData(): Data = workDataOf(KEY_RESTORE_FAILURE_REASON to ordinal)

    companion object {
        private const val KEY_RESTORE_FAILURE_REASON = "key_restore_failure_reason"

        fun fromWorkData(data: Data?): RestoreFailureReason {
            if (data == null) return UNKNOWN_ERROR
            val ordinal = data.getInt(KEY_RESTORE_FAILURE_REASON, -1)
            return entries.getOrElse(ordinal) { UNKNOWN_ERROR }
        }
    }
}
