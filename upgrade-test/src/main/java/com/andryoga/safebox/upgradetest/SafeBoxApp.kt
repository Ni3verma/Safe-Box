package com.andryoga.safebox.upgradetest

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice

/**
 * App-level steps shared by [UpgradeTest] and [RestoreTest]: launching, signing up, pointing the
 * app at a backup folder, and restoring a backup.
 *
 * Everything the previous release (N-1) has to do goes through here with **label** lookups only,
 * because N-1 predates the test tags; the one tag used here ([RESTORE_BUTTON_TAG]) is only reached
 * on the build under test. Labels are app *resource names*, resolved against whichever build is
 * installed ([AppStrings], ADR-0003), so this file holds no displayed text of its own.
 *
 * Build one per test method: [AppStrings] is bound to the APK installed when it was created.
 */
internal class SafeBoxApp {

    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val ui = UiSupport(device)
    val app = AppStrings(InstrumentationRegistry.getInstrumentation().context, APP_PACKAGE)

    /**
     * Reads an instrumentation argument, failing loudly when it is absent.
     *
     * No default on purpose: a default would let a hand-run `am instrument` silently disagree with
     * what the host actually pushed, which is the kind of quiet mismatch this harness exists to
     * catch.
     *
     * @param name argument name, passed by the host as `-e <name> <value>`
     * @return the argument's value
     */
    fun argument(name: String): String {
        val value = InstrumentationRegistry.getArguments().getString(name)
            ?: error(
                "missing instrumentation argument '$name'. The scripts/run-*-test.sh runners " +
                    "pass it; a hand-run invocation needs -e $name <value>",
            )
        logStep("argument $name=${value.take(LOGGED_ARGUMENT_CHARS)}")
        return value
    }

    /**
     * Starts the app and waits for the sign-up screen.
     */
    fun launchToSignUp() = launch(app.label(SIGNUP_HEADING))

    /**
     * Starts the app and waits for the unlock screen.
     *
     * The host force-stops the app before every phase, so a launch always lands here once an
     * account exists: the app asks for the password on every cold start.
     */
    fun launchToUnlock() = launch(app.label(UNLOCK_HEADING))

    /** Signs up with [FIXED_PASSWORD] and [PASSWORD_HINT] and waits for the sign-up screen to go. */
    fun signUp() {
        logStep("sign up")
        val signUpButton = app.label(SIGNUP_BUTTON)
        ui.typeInto(app.formLabel(SIGNUP_PASSWORD_LABEL, isMandatory = true), FIXED_PASSWORD)
        ui.typeInto(app.formLabel(SIGNUP_HINT_LABEL, isMandatory = true), PASSWORD_HINT)
        ui.clickObject(By.text(signUpButton).enabled(true))
        check(ui.awaitGone(By.text(app.label(SIGNUP_HEADING)), SIGN_UP_TIMEOUT_MS)) {
            "still on the sign-up screen after tapping '$signUpButton'${ui.describeScreen()}"
        }
    }

    /**
     * Unlocks with [FIXED_PASSWORD]. For phases that do not test the unlock screen itself.
     */
    fun unlock() = UnlockScreen(ui, app).unlock(FIXED_PASSWORD)

    /**
     * Points the app at the backup folder the host created, through the system tree picker, and
     * checks the app reports that exact folder.
     *
     * The folder is asserted as `primary:<dir>` rather than by name alone: the app renders the
     * whole tree URI, and a grant over some *other* folder ending in the same word would otherwise
     * pass.
     *
     * @param backupDir the directory's name at the root of shared storage
     */
    fun setBackupLocation(backupDir: String) {
        logStep("set backup folder '$backupDir'")
        ui.clickText(app.label(BACKUP_TAB))
        ui.clickText(app.label(SET_LOCATION_BUTTON))
        SafDocumentPicker(ui).selectFolder(backupDir)
        assertBackupLocation(backupDir)
    }

    /**
     * Asserts the Backup & Restore tab still reports [backupDir] as the backup folder.
     *
     * @param backupDir the directory's name at the root of shared storage
     */
    fun assertBackupLocation(backupDir: String) {
        ui.clickText(app.label(BACKUP_TAB))
        ui.awaitText(app.label(BACKUP_LOCATION_SET_MESSAGE))
        checkNotNull(ui.findOrNull(By.textContains("$GRANTED_TREE_PREFIX$backupDir"))) {
            "the backup screen says a location is set, but not to '$backupDir'${ui.describeScreen()}"
        }
    }

    /**
     * Restores a backup into an empty vault through the records screen's "Restore data" button.
     *
     * That button only exists while the vault is empty, so reaching it also checks sign-up left an
     * empty vault. Works on N-1 and N alike: it is label-matched.
     *
     * @param fileName the backup's name in Downloads
     * @param password the password the backup was made under
     */
    fun restoreIntoEmptyVault(fileName: String, password: String) {
        logStep("restore '$fileName' into the empty vault")
        ui.clickText(app.label(RECORDS_TAB))
        ui.clickText(app.label(RESTORE_DATA_BUTTON))
        pickAndConfirm(fileName, password)
        awaitRestoreResult(RESTORE_SUCCESS_MESSAGE, OK_BUTTON)
        check(ui.awaitGone(By.text(app.label(RESTORE_DATA_BUTTON)))) {
            "the app reported a successful restore, but the records screen is still empty" +
                ui.describeScreen()
        }
    }

    /**
     * Restores a backup over the current vault through the Backup & Restore tab. Build under test
     * only: the Restore button is found by its test tag.
     *
     * @param fileName the backup's name in Downloads
     * @param password the password the backup was made under
     */
    fun restoreOverVault(fileName: String, password: String) {
        openRestoreFromBackupTab(fileName, password)
        awaitRestoreResult(RESTORE_SUCCESS_MESSAGE, OK_BUTTON)
    }

    /**
     * Attempts a restore through the Backup & Restore tab that the app must refuse, and dismisses
     * the refusal. Build under test only.
     *
     * @param fileName the backup's name in Downloads
     * @param password the password to try
     * @param refusal which refusal the app must show
     */
    fun restoreExpectingRefusal(fileName: String, password: String, refusal: Refusal) {
        openRestoreFromBackupTab(fileName, password)
        awaitRestoreResult(refusal.messageResource, refusal.dismissResource)
    }

    /**
     * How the restore dialog refuses a file, and how that refusal is dismissed. The dialog keeps
     * the password field open after a wrong password (dismissed with Cancel), but replaces it with
     * a message after a damaged file (dismissed with OK).
     *
     * @property messageResource the message's string resource
     * @property dismissResource the dismissing button's string resource
     */
    enum class Refusal(val messageResource: String, val dismissResource: String) {
        WRONG_PASSWORD("incorrect_pswrd_message", "common_cancel"),
        DAMAGED_FILE("restore_corrupt_file_message", "common_ok"),
    }

    private fun openRestoreFromBackupTab(fileName: String, password: String) {
        logStep("restore '$fileName' from the Backup & Restore tab")
        ui.clickText(app.label(BACKUP_TAB))
        ui.retryingOnStale { ui.scrollTo(By.res(RESTORE_BUTTON_TAG)).click() }
        pickAndConfirm(fileName, password)
    }

    private fun pickAndConfirm(fileName: String, password: String) {
        SafDocumentPicker(ui).selectFromDownloads(fileName)
        ui.awaitText(app.label(RESTORE_PROMPT))
        ui.typeInto(app.label(RESTORE_PASSWORD_LABEL), password)
        ui.clickText(app.label(CONFIRM_BUTTON))
    }

    /**
     * Waits for the restore dialog's outcome and dismisses it.
     *
     * The restore runs through a WorkManager worker that decrypts and re-encrypts every record, so
     * it is allowed far longer than an ordinary UI transition.
     */
    private fun awaitRestoreResult(messageResource: String, dismissResource: String) {
        logStep("waiting for restore outcome '$messageResource'")
        ui.awaitText(app.label(messageResource), RESTORE_TIMEOUT_MS)
        logStep("restore outcome shown: '$messageResource'")
        ui.clickText(app.label(dismissResource))
    }

    /**
     * Starts the app under test and waits for the screen the caller expects, re-launching it if
     * the screen does not appear.
     *
     * Waiting on the *expected screen* rather than on "the app owns the foreground window" is
     * deliberate. On a device with an enrolled fingerprint the unlock screen raises a system
     * biometric sheet drawn by SystemUI, so the app is not the foreground package even though it
     * is running perfectly (Pixel 8 API 35 emulator, 2026-09-22). Backing out of the sheet is what
     * a user who wants to type their password does. On gesture navigation that back press was seen
     * to also send the task home, hence every attempt starts the launch intent again; the prompt is
     * not re-armed, because the app only offers biometric unlock once per process.
     *
     * @param expectedHeading text that identifies the screen the app should land on
     */
    private fun launch(expectedHeading: String) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?: error(
                "no launch intent for $APP_PACKAGE - it is not installed, or the <queries> entry " +
                    "in this module's manifest no longer matches its applicationId",
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        repeat(LAUNCH_ATTEMPTS) { attempt ->
            logStep("launch $APP_PACKAGE, attempt ${attempt + 1}, expecting '$expectedHeading'")
            context.startActivity(intent)
            val timeout = if (attempt == 0) LAUNCH_TIMEOUT_MS else UiSupport.FIND_TIMEOUT_MS
            if (ui.findOrNull(By.text(expectedHeading), timeout) != null) return
            logStep(
                "'$expectedHeading' not shown after ${timeout}ms, foreground " +
                    "'${device.currentPackageName}'; pressing back",
            )
            device.pressBack()
        }
        error(
            "'$expectedHeading' never appeared in $LAUNCH_ATTEMPTS attempts at launching " +
                "$APP_PACKAGE, even after dismissing a possible system prompt${ui.describeScreen()}",
        )
    }

    companion object {
        const val APP_PACKAGE = "com.andryoga.safebox.qa"

        /**
         * The master password of every vault the harness creates and the password of every backup
         * it takes or restores, except `format-2.bak`. Mirrors `FIXED_BACKUP_PASSWORD` in
         * scripts/lib/backup-files.sh. It satisfies every release's password rules, including v1's
         * stricter ones.
         */
        const val FIXED_PASSWORD = "Upgrade@@Test123"
        const val PASSWORD_HINT = "upgrade fixture"

        /**
         * Any password but [FIXED_PASSWORD]. Its length differs on purpose: a masked field only
         * reveals its length, and [UiSupport.retypeMasked] waits on that length to know the right
         * password has replaced this one.
         */
        const val WRONG_PASSWORD = "Wrong@Pass1"

        // Instrumentation arguments. The host owns these values because it creates the folder
        // and pushes the files; see scripts/run-upgrade-test.sh and scripts/run-restore-test.sh.
        const val BACKUP_DIR_ARGUMENT = "backupDir"
        const val RESTORE_FILE_ARGUMENT = "restoreFile"
        const val RESTORE_PASSWORD_ARGUMENT = "restorePassword"
        const val DAMAGED_FILE_ARGUMENT = "damagedFile"

        // Mirrors TestTags.RESTORE_BUTTON in :app, which this module cannot depend on (ADR-0002).
        private const val RESTORE_BUTTON_TAG = "restore_button"

        // Resource names, resolved against the installed build. See ADR-0003.
        private const val SIGNUP_HEADING = "welcome"
        private const val UNLOCK_HEADING = "welcome_back"
        private const val SIGNUP_BUTTON = "signup"
        private const val SIGNUP_PASSWORD_LABEL = "password"
        private const val SIGNUP_HINT_LABEL = "hint"
        private const val RESTORE_PASSWORD_LABEL = "password"
        private const val RESTORE_DATA_BUTTON = "restore_records_button"
        private const val RESTORE_PROMPT = "new_restore_dialog_body_text"
        private const val CONFIRM_BUTTON = "confirm"
        private const val RESTORE_SUCCESS_MESSAGE = "restore_complete_message"
        private const val OK_BUTTON = "common_ok"
        private const val RECORDS_TAB = "bottom_nav_records"

        // "Backup & Restore" is also the app bar's title once that tab is open; tapping the label
        // then hits the title, which is harmless because the tab is already showing.
        private const val BACKUP_TAB = "bottom_nav_backup_and_restore"
        private const val SET_LOCATION_BUTTON = "backup_set_location"
        private const val BACKUP_LOCATION_SET_MESSAGE = "backup_set_message"

        // How a tree over a directory at the root of shared storage is spelled in the URI the app
        // displays, e.g. "/tree/primary:SafeBoxUpgradeTest". Platform syntax, not an app label.
        private const val GRANTED_TREE_PREFIX = "primary:"

        private const val LAUNCH_TIMEOUT_MS = 30_000L
        private const val SIGN_UP_TIMEOUT_MS = 15_000L
        private const val RESTORE_TIMEOUT_MS = 60_000L

        // Enough for the one recoverable cause seen so far (a system prompt stealing the window,
        // plus the back press that dismisses it landing on the launcher).
        private const val LAUNCH_ATTEMPTS = 3

        // expectedRows is kilobytes of base64; the start is enough to tell which file it was.
        private const val LOGGED_ARGUMENT_CHARS = 60
    }
}
