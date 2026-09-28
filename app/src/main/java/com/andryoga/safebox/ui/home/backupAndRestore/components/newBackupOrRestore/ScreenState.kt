package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import android.net.Uri

data class NewBackupOrRestoreScreenState(
    val workflowState: WorkflowState = WorkflowState.ASK_FOR_PASSWORD,
    val defaultPassword: String = ""
)

enum class WorkflowState {
    // default state. ask user for the password
    ASK_FOR_PASSWORD,

    // incorrect password entered by the user. show error.
    WRONG_PASSWORD,

    // selected backup file is corrupted or has an invalid structure.
    CORRUPT_FILE,

    // selected backup file was created by a newer app version. user must update the app.
    BACKUP_TOO_NEW,

    // selected backup file has no records. nothing was restored.
    BACKUP_EMPTY,

    // backup/restore job is in progress
    IN_PROGRESS,

    // backup/restore job is completed successfully
    SUCCESS,

    // backup/restore job failed.
    FAILED,

    // backup found an empty vault, so no file was written.
    BACKUP_NOTHING_TO_BACKUP,

    // backup folder is gone or its access was revoked. The folder setting was cleared.
    BACKUP_FOLDER_INACCESSIBLE,

    // backup file could not be written into a reachable folder, e.g. the disk is full.
    BACKUP_WRITE_FAILED,

    // backup failed for an unexpected reason. Retrying fails the same way, so no password field.
    BACKUP_UNKNOWN_ERROR,
}

// the operation with which this workflow is started. Based on the operation we need to show different UI and run different business logic.
sealed class Operation {
    object Backup : Operation()
    data class Restore(val fileUri: Uri?) : Operation()
}