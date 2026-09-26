package com.andryoga.safebox.upgradetest

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.BACKUP_DIR_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.DAMAGED_FILE_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.FIXED_PASSWORD
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.RESTORE_FILE_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.RESTORE_PASSWORD_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.WRONG_PASSWORD
import com.andryoga.safebox.upgradetest.SafeBoxApp.Refusal
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 2: the build under test restores every backup format users may hold, and its own.
 *
 * scripts/run-restore-test.sh drives these methods one per invocation on a fresh install of the
 * build under test: [setUpFreshInstall] once, then one restore method per file (format-1,
 * format-2, …, the seed, then N's own backup of the seed as the round trip), then
 * [failedRestoresLeaveVaultUntouched]. After each restore the host pulls the backup the method
 * took and compares it field by field with the file that was restored.
 *
 * The screen checks run after **every** file, not just the newest: an older format leaves fields
 * empty that newer ones fill, and a list or detail screen crashing on such a record would pass the
 * backup comparison.
 */
@RunWith(AndroidJUnit4::class)
class RestoreTest {

    private lateinit var safeBox: SafeBoxApp

    @Before
    fun setUp() {
        safeBox = SafeBoxApp()
    }

    /**
     * Signs up, sets the backup folder, and turns auto-backup off, so each later backup step
     * leaves exactly one file for the host to pull.
     */
    @Test
    fun setUpFreshInstall() {
        safeBox.launchToSignUp()
        safeBox.signUp()
        safeBox.setBackupLocation(safeBox.argument(BACKUP_DIR_ARGUMENT))
        SettingsChanger(safeBox.ui, safeBox.app).applyNonDefaults()
    }

    /**
     * The first restore, into the still-empty vault, through the records screen's "Restore data"
     * button; then the screen checks and a backup.
     */
    @Test
    fun restoreIntoEmptyVault() {
        safeBox.launchToUnlock()
        safeBox.unlock()
        safeBox.restoreIntoEmptyVault(
            safeBox.argument(RESTORE_FILE_ARGUMENT),
            safeBox.argument(RESTORE_PASSWORD_ARGUMENT),
        )
        checkScreensAndBackUp()
    }

    /**
     * Every later restore, over the previous file's records, through the Backup & Restore tab;
     * then the screen checks and a backup. The row check also proves the restore *replaced* the
     * vault: a leftover record from the previous file is reported as unexpected.
     */
    @Test
    fun restoreOverVault() {
        safeBox.launchToUnlock()
        safeBox.unlock()
        safeBox.restoreOverVault(
            safeBox.argument(RESTORE_FILE_ARGUMENT),
            safeBox.argument(RESTORE_PASSWORD_ARGUMENT),
        )
        checkScreensAndBackUp()
    }

    /**
     * A damaged file and a wrong password are both refused with their own message, and neither
     * touches the vault: the host compares the backup taken afterwards with the one taken before.
     */
    @Test
    fun failedRestoresLeaveVaultUntouched() {
        safeBox.launchToUnlock()
        safeBox.unlock()
        safeBox.restoreExpectingRefusal(
            safeBox.argument(DAMAGED_FILE_ARGUMENT),
            FIXED_PASSWORD,
            Refusal.DAMAGED_FILE,
        )
        safeBox.restoreExpectingRefusal(
            safeBox.argument(RESTORE_FILE_ARGUMENT),
            WRONG_PASSWORD,
            Refusal.WRONG_PASSWORD,
        )
        BackupMaker(safeBox.ui, safeBox.app).backUp(FIXED_PASSWORD)
    }

    private fun checkScreensAndBackUp() {
        RecordsCheck(
            safeBox.ui,
            safeBox.app,
            RecordsCheck.decodeExpectedRows(
                safeBox.argument(RecordsCheck.EXPECTED_ROWS_ARGUMENT),
                safeBox.app,
            ),
        ).run()
        BackupMaker(safeBox.ui, safeBox.app).backUp(FIXED_PASSWORD)
    }
}
