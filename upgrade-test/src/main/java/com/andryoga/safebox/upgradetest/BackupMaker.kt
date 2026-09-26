package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By

/**
 * Takes a manual backup through the Backup & Restore tab, on the build under test.
 *
 * After an upgrade this is also the only real test of the backup folder's *platform grant*: the
 * app can still say a folder is set while the persisted SAF permission behind it is gone, and only
 * a write through it proves it survived.
 *
 * The host empties the backup folder before the phase that calls this and pulls the single file
 * it finds afterwards, then compares it with the file that was restored.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class BackupMaker(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Backs the vault up under [password] and waits for the app to report success.
     *
     * The Backup button carries the same text as its section heading, so it is found by its test
     * tag. Only the build under test runs this, so the tag is always there.
     *
     * @param password the backup password
     */
    fun backUp(password: String) {
        ui.clickText(app.label(BACKUP_TAB))
        ui.scrollTo(By.res(BACKUP_BUTTON_TAG)).click()

        ui.awaitText(app.label(BACKUP_PROMPT))
        ui.typeInto(app.label(PASSWORD_LABEL), password)
        ui.clickText(app.label(CONFIRM_BUTTON))

        // The backup is a WorkManager job that decrypts and re-encrypts every record, so it is
        // allowed far longer than an ordinary UI transition.
        ui.awaitText(app.label(BACKUP_SUCCESS_MESSAGE), BACKUP_TIMEOUT_MS)
        ui.clickText(app.label(OK_BUTTON))
    }

    private companion object {
        // Mirrors TestTags.BACKUP_BUTTON in :app, which this module cannot depend on (ADR-0002).
        const val BACKUP_BUTTON_TAG = "backup_button"

        // Resource names, resolved against the installed build. See ADR-0003.
        const val BACKUP_TAB = "bottom_nav_backup_and_restore"
        const val BACKUP_PROMPT = "new_backup_dialog_body_text"
        const val PASSWORD_LABEL = "password"
        const val CONFIRM_BUTTON = "confirm"
        const val BACKUP_SUCCESS_MESSAGE = "backup_complete_message"
        const val OK_BUTTON = "common_ok"
        const val BACKUP_TIMEOUT_MS = 60_000L
    }
}
