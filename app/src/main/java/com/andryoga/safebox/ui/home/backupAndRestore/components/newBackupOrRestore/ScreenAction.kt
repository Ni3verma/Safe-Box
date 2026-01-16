package com.andryoga.safebox.ui.home.backupAndRestore.components.newBackupOrRestore

sealed interface ScreenAction {
    /**
     * password is entered by the user. now we can have two scenarios:
     * 1. we need to validate it and start the backup process.
     * 2. user enters the password that was used to make the selected backup file and we need to
     * starts restore process.
     * */
    data class PasswordConfirmed(val password: String) : ScreenAction
}