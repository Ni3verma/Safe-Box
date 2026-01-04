package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri

data class ScreenState(
    // this controls what to show on the UI in the backup section.
    val backupState: BackupState = Loading,

    // this controls when to start the new backup or restore workflow.
    val newBackupOrRestoreScreenState: NewBackupOrRestoreScreenState = NewBackupOrRestoreScreenState.NotStarted,
)

sealed class BackupState

// initial state. we are checking if backup path is set or not.
object Loading : BackupState()

// backup path is not set, so show UI where user can set a new backup path.
class BackupPathNotSet : BackupState()

// backup path is already set by the user.
class BackupPathSet(val backupPath: String, val backupTime: String) : BackupState()

sealed class NewBackupOrRestoreScreenState {
    // workflow is not yet started. This is the default state.
    object NotStarted : NewBackupOrRestoreScreenState()

    // workflow needs to be started to take a new backup.
    object StartedForBackup : NewBackupOrRestoreScreenState()

    // workflow needs to be started to restore a file.
    data class StartedForRestore(
        val fileUri: Uri?
    ) : NewBackupOrRestoreScreenState()
}