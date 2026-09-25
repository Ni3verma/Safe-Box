package com.andryoga.safebox.upgradetest

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end proof that the vault survives the shipped QA APK being upgraded in place.
 *
 * Phase A establishes state on the baseline build — an account, and a vault seeded from a golden
 * backup through the real system document picker — and records it. Phase B is the host replacing
 * the APK. Phase C, on the upgraded build, asserts that the account, the hint and every recorded
 * fact about the vault are still exactly as they were, then adds an authenticator and checks its
 * code. Phase D backs the upgraded vault up, has the host clear the app's data, and restores that
 * backup into the empty app.
 *
 * The tests are **ordered and stateful across processes**, which is unusual and deliberate:
 * [seedVaultOnBaselineBuild] runs against the baseline APK and the rest against the build under
 * test, after the host has replaced the app underneath, and the two halves of Phase D run either
 * side of the host's `pm clear`. They are therefore never run in the same invocation —
 * scripts/run-upgrade-test.sh selects one at a time by method filter, which is also why a filter
 * typo has to be fatal rather than a silent zero-test pass.
 *
 * Everything is selected by what the app renders, because the baseline release has no
 * `Modifier.testTag` and the APK under test is minified. The harness nonetheless holds no rendered
 * text of its own: every label below is an app *resource name*, resolved against whichever build is
 * installed, per
 * [ADR-0003](../../../../../../../../docs/decisions/0003-ui-labels-from-resource-names.md). Phase A
 * therefore drives the **baseline release**'s wording without that wording appearing anywhere in
 * this file, and the same names resolve to the build under test in the later phases.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeSmokeTest {

    private lateinit var device: UiDevice
    private lateinit var ui: UiSupport
    private lateinit var app: AppStrings

    /**
     * [AppStrings] is built per test method rather than once per class on purpose: it is bound to
     * the APK that was installed when it was created, and the two phases run against two different
     * builds. Rebuilding it here makes that safe by construction instead of by discipline.
     */
    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ui = UiSupport(device)
        app = AppStrings(InstrumentationRegistry.getInstrumentation().context, APP_PACKAGE)
    }

    /**
     * Phase A. Creates an account on the baseline build and seeds it from a golden backup.
     *
     * Signing up is the cheapest action that makes the upgrade worth testing at all: it writes the
     * password hash and the hint to Room's `user_details` table - the hint encrypted under the
     * `symmetricDataKey` AndroidKeyStore alias, which signing up generates. Without it the upgraded
     * app would land on signup and the phase C assertions would be vacuous.
     *
     * Restoring a fixture on top of that is what gives later stages something to assert *about*.
     * It is done through the real picker and the real restore worker rather than by pushing a
     * database file, because a restore is the only supported way to get records into the vault
     * without ~50 taps, and it exercises the production write path while doing so.
     *
     * The phase ends by reading all of it back out into an oracle file ([VaultOracle]), and then
     * locking the app to read the password hint, which only the unlock screen shows. Reading is
     * not a formality: it is the only evidence that what the seeding *did* is what the app *has*,
     * it is what the ten-run determinism acceptance compares, and it is what Phase C is judged
     * against.
     */
    @Test
    fun seedVaultOnBaselineBuild() {
        launchAppUnderTest(app.label(SIGNUP_HEADING))
        signUp()
        restoreBackup(requiredArgument(FIXTURE_ARGUMENT))
        RecordCreator(ui, app).createAll()
        assertSeededRecordsPresent()
        setBackupLocation()
        SettingsChanger(ui, app).applyNonDefaults()
        val vault = VaultOracle(ui, app, seededTitles()).describe()
        OracleFiles.write(OracleFiles.BASELINE, vault + hintLine(lockAndReadHint()))
    }

    /**
     * Phase C. Asserts the upgraded build still holds everything Phase A left in it.
     *
     * In the plan's order: it lands on unlock rather than signup, which proves preferences
     * survived (Group 1); it shows the same hint; it refuses a wrong password and accepts the
     * right one (Group 2); and it then describes the vault exactly as the baseline did (Group 3).
     * That last comparison is the broad Keystore check. Every field of one record per type is read
     * back through the detail screen, which has to decrypt it, so a lost or regenerated
     * `symmetricDataKey` shows up there as a value that differs or a screen that fails. The app
     * regenerates a missing key without complaint, so launching proves nothing about it. In
     * practice the hint trips first, because it is encrypted under the same key: with the alias
     * deliberately wiped, Show Hint crashed the app with `AEADBadTagException` before the oracle
     * was reached (2026-09-24). The oracle is kept regardless, since it is what covers the records.
     *
     * The oracle is captured *before* the search step so that search cannot leave the list
     * filtered for the walk that counts rows.
     *
     * Then Groups 4 and 5, which only the build under test can do. An authenticator is added
     * through the UI, which writes into the table `MIGRATION_4_5` created. The code its row shows
     * must be the RFC 6238 value for its seed, computed by the harness. The vault is then described
     * again and must differ from the baseline by exactly that one record (step 12). That second
     * oracle is what Phase D's restored vault is held to.
     *
     * Crashes and ANRs are the host's job (Group 9), because a crash in the app under test does not
     * fail this process; scripts/run-upgrade-test.sh scans logcat after every phase.
     */
    @Test
    fun verifyVaultAfterUpgrade() {
        launchAppUnderTest(app.label(UNLOCK_HEADING))
        val unlockScreen = UnlockScreen(ui, app)
        val hint = unlockScreen.readHint()
        unlockScreen.assertRejects(WRONG_PASSWORD)
        unlockScreen.unlock(MASTER_PASSWORD)

        val oracle = VaultOracle(ui, app, seededTitles()).describe() + hintLine(hint)
        OracleFiles.write(OracleFiles.UPGRADED, oracle)
        OracleFiles.assertMatchesBaseline(oracle)

        assertSearchFinds(SEARCH_TARGET, SEARCH_EXCLUDED)
        clearSearch()

        val authenticator = SeedRecord.AUTHENTICATOR
        RecordCreator(ui, app).create(authenticator)
        TotpDisplayCheck(ui, app).assertListShowsCodeFor(authenticator)
        val withAuthenticator = describeUpgradedVault()
        OracleFiles.write(OracleFiles.WITH_AUTHENTICATOR, withAuthenticator)
        OracleFiles.assertSameLines(
            "adding '${authenticator.title}' changed more than that one record.",
            VaultOracle.recordLinesAfterAdding(
                OracleFiles.read(OracleFiles.BASELINE),
                authenticator.title,
                authenticator.typeResourceName,
            ),
            VaultOracle.recordLines(withAuthenticator),
            "${OracleFiles.BASELINE} and ${OracleFiles.WITH_AUTHENTICATOR}",
        )
    }

    /**
     * Phase D, first half. Takes a backup on the upgraded build (plan step 16).
     *
     * A method of its own rather than the tail of Phase C, so that a failure is reported against
     * the phase that failed and the crash scan after it is blamed correctly. The host then pulls
     * the file, decodes it independently and clears the app's data before the second half runs.
     *
     * Unlocking again is not waste: it proves the vault still opens after Phase C wrote a record
     * into the migrated schema.
     */
    @Test
    fun backUpUpgradedVault() {
        launchAppUnderTest(app.label(UNLOCK_HEADING))
        UnlockScreen(ui, app).unlock(MASTER_PASSWORD)
        BackupMaker(ui, app).backUp(BACKUP_PASSWORD)
    }

    /**
     * Phase D, second half. Restores the upgraded build's own backup into a cleared app (steps
     * 17–18) and holds the result to Phase C's post-authenticator oracle.
     *
     * This is what catches "the upgrade worked, but the export it now produces is broken", which
     * would quietly destroy the user's only recovery path. The host ran `pm clear`, so this starts
     * from signup with a regenerated Keystore key, and only the backup file carries anything over.
     *
     * Only record lines are compared: the clear resets settings and the backup location by design.
     * The TOTP code is checked again because it is the only proof the *seed* survived the export,
     * rather than just a row with the right title.
     */
    @Test
    fun restoreBackupIntoClearedApp() {
        launchAppUnderTest(app.label(SIGNUP_HEADING))
        signUp()
        restoreBackup(requiredArgument(ROUND_TRIP_ARGUMENT))

        val restored = describeUpgradedVault()
        OracleFiles.write(OracleFiles.ROUND_TRIP, restored)
        OracleFiles.assertSameLines(
            "the vault restored from the upgraded build's backup is not the vault it backed up.",
            VaultOracle.recordLines(OracleFiles.read(OracleFiles.WITH_AUTHENTICATOR)),
            VaultOracle.recordLines(restored),
            "${OracleFiles.WITH_AUTHENTICATOR} and ${OracleFiles.ROUND_TRIP}",
        )
        TotpDisplayCheck(ui, app).assertListShowsCodeFor(SeedRecord.AUTHENTICATOR)
    }

    /** Describes a vault that holds the post-upgrade authenticator as well as Phase A's records. */
    private fun describeUpgradedVault(): String = VaultOracle(
        ui,
        app,
        seededTitles() + SeedRecord.AUTHENTICATOR.title,
        VaultOracle.ALL_TYPE_RESOURCE_NAMES,
    ).describe()

    /**
     * Locks the app and reads the hint off the unlock screen.
     *
     * Force-stopping is how the app gets locked: it asks for the password on every cold start, and
     * nothing in its UI locks it on demand. It is the same thing the host does before the upgrade,
     * so it cannot disturb any state that the upgrade is meant to carry.
     */
    private fun lockAndReadHint(): String {
        device.executeShellCommand("am force-stop $APP_PACKAGE")
        launchAppUnderTest(app.label(UNLOCK_HEADING))
        return UnlockScreen(ui, app).readHint()
    }

    private fun hintLine(hint: String) = "$HINT_KEY=$hint\n"

    /**
     * Searches the records list and asserts that it filters to the expected title.
     *
     * This is a different query path from the detail screen that the oracle reads: search goes
     * through the list's own query rather than one record's. Finding the target is only half of
     * the check. A search box that silently ignored its input would also show the target, so a
     * second title must disappear, which proves a query actually ran.
     *
     * The target is matched as a list row, never as the search field, which holds the same text
     * as soon as it has been typed. The excluded title must be on screen *before* the search, or
     * its absence afterwards would prove nothing. That is why it is chosen from the top of the
     * list.
     *
     * @param target a seeded title the search must find
     * @param excluded a seeded title, visible at the top of the unfiltered list, that the search
     * must filter out
     */
    private fun assertSearchFinds(target: String, excluded: String) {
        ui.clickText(app.label(RECORDS_TAB))
        ui.scrollToTop()
        checkNotNull(ui.findOrNull(By.text(excluded))) {
            "'$excluded' is not at the top of the unfiltered list, so filtering it out would " +
                "prove nothing${ui.describeScreen()}"
        }
        ui.retryingOnStale { ui.awaitObject(By.clazz(EDIT_TEXT_CLASS)).text = target }

        check(ui.awaitGone(By.text(excluded))) {
            "searching for '$target' still lists '$excluded', so the search did not filter" +
                ui.describeScreen()
        }
        check(ui.findAll(By.text(target)).any { it.className != EDIT_TEXT_CLASS }) {
            "searching for '$target' does not list it${ui.describeScreen()}"
        }
    }

    /**
     * Empties the search box and waits for the full list to return.
     *
     * The query outlives a trip to the add-record form, so a record created while the list is
     * filtered is saved correctly and then cannot be found on the list it returns to.
     */
    private fun clearSearch() {
        ui.retryingOnStale { ui.awaitObject(By.clazz(EDIT_TEXT_CLASS)).text = "" }
        ui.awaitText(SEARCH_EXCLUDED)
    }

    private fun signUp() {
        val signUpButton = app.label(SIGNUP_BUTTON)
        val heading = app.label(SIGNUP_HEADING)
        ui.typeInto(app.formLabel(SIGNUP_PASSWORD_LABEL, isMandatory = true), MASTER_PASSWORD)
        ui.typeInto(app.formLabel(SIGNUP_HINT_LABEL, isMandatory = true), PASSWORD_HINT)
        ui.clickObject(By.text(signUpButton).enabled(true))

        // Leaving the signup heading behind is the only "signed up" signal available without
        // asserting on home-screen content, which belongs to MR2.
        check(ui.awaitGone(By.text(heading), SIGN_UP_TIMEOUT_MS)) {
            "still on the signup screen after tapping '$signUpButton'${ui.describeScreen()}"
        }
    }

    /**
     * Restores a backup the host put in Downloads.
     *
     * Entry is through the empty vault's own "Restore data" shortcut rather than the Backup &
     * Restore tab: it is one tap instead of two, and it only exists while the vault is empty, so it
     * doubles as a check that sign-up really did leave an empty vault.
     *
     * The file's name arrives as an instrumentation argument instead of being duplicated here,
     * because the host is what puts the file on the device — two copies of that name would
     * eventually disagree, and the failure would look like a picker bug. Phase A restores the
     * golden fixture this way and Phase D the upgraded build's own backup, both under
     * [BACKUP_PASSWORD].
     *
     * @param fileName the backup's name in Downloads
     */
    private fun restoreBackup(fileName: String) {
        ui.clickText(app.label(RESTORE_DATA_BUTTON))
        SafDocumentPicker(ui).selectFromDownloads(fileName)

        ui.awaitText(app.label(RESTORE_PROMPT))
        ui.typeInto(app.label(RESTORE_PASSWORD_LABEL), BACKUP_PASSWORD)
        ui.clickText(app.label(CONFIRM_BUTTON))

        // The restore runs through a WorkManager worker that decrypts and re-encrypts every record,
        // so it is allowed far longer than an ordinary UI transition.
        ui.awaitText(app.label(RESTORE_SUCCESS_MESSAGE), RESTORE_TIMEOUT_MS)
        ui.clickText(app.label(OK_BUTTON))
    }

    /**
     * Asserts every record Phase A seeded is listed, by either route.
     *
     * "Data has been successfully restored." is the app's own claim, and a claim is not evidence: a
     * restore that decrypted the file but wrote nothing would still show it. Checking the titles
     * turns Phase A into something that fails where the defect is, rather than leaving Phase C to
     * fail confusingly against an empty vault.
     *
     * All the titles are collected before failing so the message names everything missing, not just
     * the first one, and each is labelled with how it was supposed to get there — a failure where
     * only the restored records are absent means something very different from one where only the
     * UI-created records are.
     *
     * Each lookup rewinds first. `scrollToText` only travels downwards and leaves the list wherever
     * it stopped, while these titles are checked in seeding order, which is not the list's sort
     * order — so without a rewind a title that sits *above* the previous one is reported missing
     * from a vault that contains it. Eleven records happen to fit within one scroll of each other
     * today, which is the only reason this has been passing.
     */
    private fun assertSeededRecordsPresent() {
        ui.clickText(app.label(RECORDS_TAB))

        val expected = FIXTURE_RECORD_TITLES.map { "restored:$it" } +
            SeedRecord.ALL.map { "ui:${it.title}" }
        val missing = expected.filter {
            ui.scrollToTop()
            ui.scrollToText(it.substringAfter(':')) == null
        }
        check(missing.isEmpty()) {
            "${missing.size} of ${expected.size} seeded records are not listed: " +
                "$missing${ui.describeScreen()}"
        }
    }

    /**
     * Every record title Phase A is responsible for, whichever route put it there.
     *
     * One definition, used both by the assertion above and by the oracle's own cross-check, so the
     * two can never disagree about what a correctly seeded vault contains.
     */
    private fun seededTitles(): Set<String> =
        (FIXTURE_RECORD_TITLES + SeedRecord.ALL.map { it.title }).toSet()

    /**
     * Points the app at a backup directory, through the system tree picker.
     *
     * This is the only piece of Phase A state that lives outside the app's own storage: what gets
     * saved is a SAF tree URI plus a *persisted permission grant* held by the platform on the app's
     * behalf. An in-place upgrade is supposed to keep both, and the two can fail independently —
     * the app can remember a URI it is no longer allowed to write to. Phase A therefore sets it so
     * that later stages have something real to re-check.
     *
     * The directory name arrives as an instrumentation argument for the same reason the fixture's
     * name does: the host creates the directory, so the host owns its name.
     *
     * The path is asserted against `primary:<dir>` rather than against the directory name alone.
     * The app renders the whole tree URI, and a grant over some *other* folder that merely ends in
     * the same word would otherwise read as a pass.
     */
    private fun setBackupLocation() {
        val backupDir = requiredArgument(BACKUP_DIR_ARGUMENT)

        ui.clickText(app.label(BACKUP_TAB))
        ui.clickText(app.label(SET_LOCATION_BUTTON))
        SafDocumentPicker(ui).selectFolder(backupDir)

        ui.awaitText(app.label(BACKUP_LOCATION_SET_MESSAGE))
        checkNotNull(ui.findOrNull(By.textContains("$GRANTED_TREE_PREFIX$backupDir"))) {
            "the backup screen says the location is set, but not to '$backupDir'" +
                ui.describeScreen()
        }
    }

    /**
     * Reads an instrumentation argument, failing loudly when it is absent.
     *
     * No default is provided on purpose. A default would let a hand-run `am instrument` silently
     * disagree with what the host actually pushed, which is precisely the kind of quiet mismatch
     * this harness exists to catch.
     *
     * @param name argument name, passed by the host as `-e <name> <value>`
     * @return the argument's value
     */
    private fun requiredArgument(name: String): String =
        InstrumentationRegistry.getArguments().getString(name)
            ?: error(
                "missing instrumentation argument '$name'. scripts/run-upgrade-test.sh passes it; " +
                    "a hand-run invocation needs -e $name <value>",
            )

    /**
     * Starts the app under test and waits for the screen the caller expects, re-launching it if
     * the screen does not appear.
     *
     * Waiting on the *expected screen* rather than on "the app owns the foreground window" is
     * deliberate. On any device with an enrolled fingerprint the unlock screen immediately raises a
     * system biometric sheet, which is drawn by SystemUI — the app stops being the foreground
     * package, and a `By.pkg(APP_PACKAGE).depth(0)` wait can never succeed even though the app is
     * running perfectly. Observed on a Pixel 8 API 35 emulator on 2026-09-22; CI's emulator has
     * nothing enrolled, so this would have been a flake that only ever reproduced locally.
     *
     * Backing out of the prompt is exactly what a user who wants to type their password does, and
     * the app handles it through `onErrorOrCancel`. The back press only happens after a wait has
     * fully timed out, so it cannot cut short a merely slow launch. It is not reliable on its own
     * though: on a gesture-navigation device the same press was seen to dismiss the sheet *and*
     * send the task to the launcher (`RecentsController.finishInner: toHome=true`), after which
     * the test was asserting against the home screen. Hence every attempt starts the launch intent
     * again — bringing a backgrounded task forward is harmless, and the prompt is not re-armed
     * because the app only offers biometric unlock once per process.
     *
     * @param expectedHeading text that identifies the screen the app should land on
     * @return the heading node, so callers can assert further against it
     */
    private fun launchAppUnderTest(expectedHeading: String): UiObject2 {
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?: error(
                "no launch intent for $APP_PACKAGE - it is not installed, or the <queries> entry " +
                    "in this module's manifest no longer matches its applicationId",
            )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        repeat(LAUNCH_ATTEMPTS) { attempt ->
            context.startActivity(intent)
            val timeout = if (attempt == 0) LAUNCH_TIMEOUT_MS else UiSupport.FIND_TIMEOUT_MS
            ui.findOrNull(By.text(expectedHeading), timeout)
                ?.let { return it }
            device.pressBack()
        }
        error(
            "'$expectedHeading' never appeared in $LAUNCH_ATTEMPTS attempts at launching " +
                "$APP_PACKAGE, even after dismissing a possible system prompt${ui.describeScreen()}",
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.andryoga.safebox.qa"

        // Credentials are fixed by docs/testing/upgrade-testing.md section 11, decision 2. The
        // password must satisfy PasswordValidator: mixed case, two digits, a symbol, length >= 7.
        const val MASTER_PASSWORD = "Upgrade@Test12"
        const val PASSWORD_HINT = "upgrade fixture"

        // Any password but the master one. Its length differs from MASTER_PASSWORD's on purpose:
        // the masked field only reveals its length, and UiSupport.retypeMasked waits on that
        // length to know that the right password has replaced this one.
        const val WRONG_PASSWORD = "Wrong@Pass1"

        // The oracle key for the hint. It sits beside the vault's keys, but it is read from the
        // unlock screen rather than from the vault.
        const val HINT_KEY = "unlock.hint"

        // A restored record to search for, and a UI-created one that must drop out of the results.
        // The excluded title shares the word "card", so only a real title match separates them,
        // and its "0 " prefix keeps it in the first screenful of the unfiltered list.
        const val SEARCH_TARGET = "card 2"
        const val SEARCH_EXCLUDED = "0 ui card"
        const val EDIT_TEXT_CLASS = "android.widget.EditText"

        // Independent of the master password: a .bak is encrypted under a password chosen at export
        // time. This one is recorded in upgrade-test/src/main/assets/fixtures/README.md.
        const val BACKUP_PASSWORD = "Fixture@Backup1"

        // Everything below that names a screen element is an app *resource name*, resolved against
        // whichever build is installed by AppStrings. Per ADR-0003 the harness never holds the
        // displayed text, so a copy edit in any future release changes nothing here.
        const val SIGNUP_HEADING = "welcome"
        const val UNLOCK_HEADING = "welcome_back"
        const val SIGNUP_BUTTON = "signup"

        // Mandatory signup fields are labelled by MandatoryLabelText, which appends a red asterisk
        // inside the same text node, so the accessibility text is "Password*" and not "Password" -
        // hence AppStrings.formLabel at the call site. The unlock screen uses a plain Text for the
        // same resource (see UnlockScreen), and usefully that difference also tells the two
        // screens apart.
        const val SIGNUP_PASSWORD_LABEL = "password"
        const val SIGNUP_HINT_LABEL = "hint"

        // The restore dialog's own field. Spelled separately from UnlockScreen's label despite
        // resolving the same resource: they are different screens owned by different code, and
        // collapsing them would make a change to either look safe when it is not.
        const val RESTORE_PASSWORD_LABEL = "password"

        const val RESTORE_DATA_BUTTON = "restore_records_button"
        const val RESTORE_PROMPT = "new_restore_dialog_body_text"
        const val CONFIRM_BUTTON = "confirm"
        const val RESTORE_SUCCESS_MESSAGE = "restore_complete_message"
        const val OK_BUTTON = "common_ok"
        const val RECORDS_TAB = "bottom_nav_records"
        const val BACKUP_TAB = "bottom_nav_backup_and_restore"

        // The backup screen before and after a directory is granted. "Backup & Restore" is also the
        // app bar's title once that tab is open, so it is only safe to tap while another tab is
        // showing - which is the only place setBackupLocation() taps it.
        const val SET_LOCATION_BUTTON = "backup_set_location"
        const val BACKUP_LOCATION_SET_MESSAGE = "backup_set_message"

        // How a tree over a directory at the root of the device's own shared storage is spelled in
        // the URI the app displays, e.g. "/tree/primary:SafeBoxUpgradeTest". Platform syntax rather
        // than an app label, so there is no resource to resolve it from.
        const val GRANTED_TREE_PREFIX = "primary:"

        const val FIXTURE_ARGUMENT = "fixtureFile"
        const val BACKUP_DIR_ARGUMENT = "backupDir"
        const val ROUND_TRIP_ARGUMENT = "roundTripFile"

        // Titles of every record in v2_pre_totp.bak: 2 logins, 2 bank accounts, 2 cards, 1 note.
        // Source of truth is the fixture README, which was produced by decrypting the file rather
        // than by reading it off a screen.
        val FIXTURE_RECORD_TITLES = listOf(
            "login 1",
            "login 2",
            "BA 1",
            "ba 2",
            "card 1",
            "card 2",
            "note",
        )

        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val SIGN_UP_TIMEOUT_MS = 15_000L
        const val RESTORE_TIMEOUT_MS = 60_000L

        // Two extra tries are enough for the one recoverable cause seen so far (a system prompt
        // stealing the window, plus the back press that dismisses it landing on the launcher).
        // Anything still failing after that is a real defect and should fail fast.
        const val LAUNCH_ATTEMPTS = 3
    }
}
