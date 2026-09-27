package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import androidx.work.Data
import androidx.work.workDataOf
import com.andryoga.safebox.worker.BackupDataWorker

/**
 * Type-safe outcomes emitted by [BackupDataWorker] in its failure output data, so the manual
 * backup dialog can show a reason-specific message.
 *
 * **Append new entries at the end only.** [toWorkData] serializes the [ordinal], and that value
 * crosses the WorkManager `Data` boundary, where it can outlive an app upgrade while the work is
 * still enqueued. Reordering or inserting would silently remap an in-flight failure reason.
 * `BackupFailureReasonTest` pins every ordinal to enforce this.
 */
enum class BackupFailureReason {
    // Vault is empty, so no file was written.
    NOTHING_TO_BACKUP,

    // Backup folder is gone or its access was revoked. The folder setting is cleared.
    FOLDER_INACCESSIBLE,

    // Folder is reachable but the file could not be written, e.g. the disk is full.
    WRITE_FAILED,

    // An unclassified or unexpected error, e.g. while reading the DB or encrypting.
    UNKNOWN,
    ;

    fun toWorkData(): Data = workDataOf(KEY_BACKUP_FAILURE_REASON to ordinal)

    companion object {
        private const val KEY_BACKUP_FAILURE_REASON = "key_backup_failure_reason"

        fun fromWorkData(data: Data?): BackupFailureReason {
            if (data == null) return UNKNOWN
            val ordinal = data.getInt(KEY_BACKUP_FAILURE_REASON, -1)
            return entries.getOrElse(ordinal) { UNKNOWN }
        }
    }
}
