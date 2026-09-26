package com.andryoga.safebox.upgradetest

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.BACKUP_DIR_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.FIXED_PASSWORD
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.PASSWORD_HINT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.RESTORE_FILE_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.RESTORE_PASSWORD_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.WRONG_PASSWORD
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 1: the vault survives the previous release (N-1) being upgraded in place to the build
 * under test (N).
 *
 * The two methods run against **two different APKs**, with the host's `adb install -r` in
 * between, so they are never run in the same invocation: scripts/run-upgrade-test.sh selects one
 * at a time by method name, which is also why a zero-test run is fatal there.
 *
 * The host then decodes the backup N took and compares it field by field with the seed N-1
 * restored; that comparison is the data check. The methods here cover what only the UI can: the
 * account, the hint, the password check, the screens, settings and the backup folder's grant.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {

    private lateinit var safeBox: SafeBoxApp

    /** Rebuilt per method: each runs against a different installed APK. */
    @Before
    fun setUp() {
        safeBox = SafeBoxApp()
    }

    /**
     * On N-1: create the state the upgrade must carry.
     *
     * Sign-up writes the password hash and the encrypted hint; restoring the seed fills the vault
     * through the real picker and restore worker; the backup folder is a persisted platform grant;
     * two settings move off their defaults. Every lookup here is by label, because N-1 has no test
     * tags.
     */
    @Test
    fun prepareOnPreviousRelease() {
        safeBox.launchToSignUp()
        safeBox.signUp()
        safeBox.restoreIntoEmptyVault(
            safeBox.argument(RESTORE_FILE_ARGUMENT),
            safeBox.argument(RESTORE_PASSWORD_ARGUMENT),
        )
        safeBox.setBackupLocation(safeBox.argument(BACKUP_DIR_ARGUMENT))
        SettingsChanger(safeBox.ui, safeBox.app).applyNonDefaults()
    }

    /**
     * On N, after the upgrade: everything N-1 left is still there, and N can back it up.
     *
     * It lands on unlock rather than sign-up (preferences survived), shows the same hint (the
     * Keystore key survived: the hint is encrypted under it), refuses a wrong password and accepts
     * the right one (the hash survived). Then the shared screen checks, the settings, the backup
     * folder, and a backup through that folder's grant for the host to compare.
     */
    @Test
    fun verifyAfterUpgrade() {
        safeBox.launchToUnlock()
        val unlockScreen = UnlockScreen(safeBox.ui, safeBox.app)
        val hint = unlockScreen.readHint()
        check(hint == PASSWORD_HINT) {
            "the unlock screen shows hint '$hint' after the upgrade, not '$PASSWORD_HINT'"
        }
        unlockScreen.assertRejects(WRONG_PASSWORD)
        unlockScreen.unlock(FIXED_PASSWORD)

        RecordsCheck(
            safeBox.ui,
            safeBox.app,
            RecordsCheck.decodeExpectedRows(
                safeBox.argument(RecordsCheck.EXPECTED_ROWS_ARGUMENT),
                safeBox.app,
            ),
        ).run()
        SettingsChanger(safeBox.ui, safeBox.app).assertNonDefaults()
        safeBox.assertBackupLocation(safeBox.argument(BACKUP_DIR_ARGUMENT))
        BackupMaker(safeBox.ui, safeBox.app).backUp(FIXED_PASSWORD)
    }
}
