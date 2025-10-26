package com.andryoga.composeapp.ui.home.backupAndRestore

sealed interface ScreenAction {
    object SetBackupPathClick : ScreenAction
    object EditBackupPathClick : ScreenAction
    object BackupClick : ScreenAction
    object RestoreClick : ScreenAction
}