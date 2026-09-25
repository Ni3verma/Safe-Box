package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By

/**
 * Takes a manual backup through the Backup & Restore tab (plan Group 6, step 16).
 *
 * This is also the only real test of the backup location's *platform grant* after an upgrade. The
 * oracle records only that the app still says a location is set; the persisted SAF permission
 * behind it can be lost independently, and only a write through it proves it survived.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class BackupMaker(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Backs the vault up under [password] and waits for the app to report success.
     *
     * The Backup button shares its text with the section heading above it, so it is told apart by
     * geometry: it is the one on the same line as Edit Path, which sits beside it in the button row.
     *
     * @param password the backup password, independent of the master password
     */
    fun backUp(password: String) {
        ui.clickText(app.label(BACKUP_TAB))
        val editPath = app.label(EDIT_PATH_BUTTON)
        checkNotNull(ui.scrollToText(editPath)) {
            "the backup screen has no '$editPath' button, so no backup location is set" +
                ui.describeScreen()
        }
        val screen = ui.textSnapshot()
        val editPathText = screen.first { it.text == editPath }
        val backupButton = screen.firstOrNull {
            it.text == app.label(BACKUP_BUTTON) && it.sharesLineWith(editPathText)
        } ?: error("no '${app.label(BACKUP_BUTTON)}' button beside '$editPath'${ui.describeScreen()}")
        ui.device.click(backupButton.bounds.centerX(), backupButton.bounds.centerY())

        ui.awaitText(app.label(BACKUP_PROMPT))
        ui.typeInto(app.label(PASSWORD_LABEL), password)
        ui.clickText(app.label(CONFIRM_BUTTON))

        // The backup is a WorkManager job that decrypts and re-encrypts every record, like the
        // restore, so it gets the same allowance.
        ui.awaitText(app.label(BACKUP_SUCCESS_MESSAGE), BACKUP_TIMEOUT_MS)
        ui.clickObject(By.text(app.label(OK_BUTTON)))
    }

    private companion object {
        // Resource names, resolved against the installed build. See ADR-0003.
        const val BACKUP_TAB = "bottom_nav_backup_and_restore"
        const val EDIT_PATH_BUTTON = "backup_edit_path"
        const val BACKUP_BUTTON = "backup"
        const val BACKUP_PROMPT = "new_backup_dialog_body_text"
        const val PASSWORD_LABEL = "password"
        const val CONFIRM_BUTTON = "confirm"
        const val BACKUP_SUCCESS_MESSAGE = "backup_complete_message"
        const val OK_BUTTON = "common_ok"
        const val BACKUP_TIMEOUT_MS = 60_000L
    }
}
