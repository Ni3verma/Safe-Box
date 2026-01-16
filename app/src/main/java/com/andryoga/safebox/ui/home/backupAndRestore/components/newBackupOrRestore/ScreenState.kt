package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

import android.net.Uri

data class NewBackupOrRestoreScreenState(
    val workflowState: WorkflowState = WorkflowState.ASK_FOR_PASSWORD
)

enum class WorkflowState {
    // default state. ask user for the password
    ASK_FOR_PASSWORD,

    // incorrect password entered by the user. show error.
    WRONG_PASSWORD,

    // backup/restore job is in progress
    IN_PROGRESS,

    // backup/restore job is completed successfully
    SUCCESS,

    // backup/restore job failed.
    FAILED
}

// the operation with which this workflow is started. Based on the operation we need to show different UI and run different business logic.
sealed class Operation {
    object Backup : Operation()
    data class Restore(val fileUri: Uri?) : Operation()
}