package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri

sealed interface ScreenAction {
    data class BackupPathSelected(val uri: Uri?) : ScreenAction
    object NewBackupClick : ScreenAction
    object NewBackupDismiss : ScreenAction
    data class NewBackupRequest(val password: String) : ScreenAction
    object RestoreClick : ScreenAction
}