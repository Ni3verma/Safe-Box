package com.andryoga.composeapp.ui.home.backupAndRestore

import android.net.Uri

sealed interface ScreenAction {
    data class BackupPathSelected(val uri: Uri?) : ScreenAction
    object EditBackupPathClick : ScreenAction
    object BackupClick : ScreenAction
    object RestoreClick : ScreenAction
}