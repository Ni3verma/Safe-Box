package com.andryoga.safebox.ui.home.backupAndRestore

import android.net.Uri

sealed interface ScreenAction {
    // when backup path is selected or edited.
    data class BackupPathSelected(val uri: Uri?) : ScreenAction

    // when new backup button is clicked and now password is required from user.
    object NewBackupClick : ScreenAction

    // when new backup or restore dialog is dismissed. It can be because of any positive/negative result.
    object NewBackupOrRestoreDismiss : ScreenAction

    // when restore button is clicked by user and file is selected from storage. Now password is required from user.
    data class RestoreFileSelected(val uri: Uri?) : ScreenAction
}