package com.andryoga.composeapp.ui.home.backupAndRestore

data class ScreenState(
    val backupState: BackupState = Loading(),
    val newBackupState: NewBackupState = NewBackupState.ASK_FOR_PASSWORD,
    val restoreState: RestoreState = RestoreState.NOT_STARTED
)

sealed class BackupState
class Loading : BackupState()
class BackupNotSet : BackupState()
class BackupSet(val backupPath: String, val backupTime: String) : BackupState()

enum class NewBackupState {
    NOT_STARTED,
    ASK_FOR_PASSWORD,
    VALIDATING_PASSWORD,
    WRONG_PASSWORD,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}

enum class RestoreState {
    NOT_STARTED,
    ASK_FOR_PASSWORD,
    IN_PROGRESS,
    SUCCESS,
    FAILED
}