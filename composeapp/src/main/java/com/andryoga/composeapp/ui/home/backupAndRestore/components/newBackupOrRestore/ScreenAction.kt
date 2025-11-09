package com.andryoga.composeapp.ui.home.backupAndRestore.components.newBackupOrRestore

sealed interface ScreenAction {
    // when workflow is dismissed. It can be because of any positive/negative result.
    object Dismiss : ScreenAction

    // password is entered by the user. now we need to validate it and start the appropriate work for backup/ restore.
    data class ConfirmPasswordRequest(val password: String) : ScreenAction
}