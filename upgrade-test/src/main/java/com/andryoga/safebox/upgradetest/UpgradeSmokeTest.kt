package com.andryoga.safebox.upgradetest

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end proof that the harness can drive the shipped QA APK across an in-place upgrade.
 *
 * Phase A establishes state on the baseline build — an account, and a vault seeded from a golden
 * backup through the real system document picker. Phase B asserts only that the upgraded build
 * still knows an account exists; assertions about the vault's *contents* surviving the upgrade
 * arrive in MR3.
 *
 * The two tests are **ordered and stateful across processes**, which is unusual and deliberate:
 * [seedVaultOnBaselineBuild] runs against the baseline APK and [unlockScreenAppearsAfterUpgrade]
 * runs against the build under test, after the host has replaced the app underneath. They are
 * therefore never run in the same invocation — scripts/run-upgrade-test.sh selects one at a time by
 * method filter, which is also why a filter typo has to be fatal rather than a silent zero-test
 * pass.
 *
 * Everything is selected by visible text, because production code contains no `Modifier.testTag`
 * and the APK under test is minified. Note that the text Phase A drives belongs to the **baseline
 * release**, not to this branch: reading current sources to write these selectors is the wrong
 * reference, and they were instead read off the installed baseline.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeSmokeTest {

    private lateinit var device: UiDevice
    private lateinit var ui: UiSupport

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ui = UiSupport(device)
    }

    /**
     * Phase A. Creates an account on the baseline build and seeds it from a golden backup.
     *
     * Signing up is the cheapest action that makes the upgrade worth testing at all: it writes the
     * password hash to `EncryptedSharedPreferences` and generates the `symmetricDataKey`
     * AndroidKeyStore alias. Without it the upgraded app would land on signup and the phase B
     * assertion would be vacuous.
     *
     * Restoring a fixture on top of that is what gives later stages something to assert *about*.
     * It is done through the real picker and the real restore worker rather than by pushing a
     * database file, because a restore is the only supported way to get records into the vault
     * without ~50 taps, and it exercises the production write path while doing so.
     */
    @Test
    fun seedVaultOnBaselineBuild() {
        launchAppUnderTest(SIGNUP_HEADING)
        signUp()
        restoreGoldenBackup()
        RecordCreator(ui).createAll()
        assertSeededRecordsPresent()
    }

    /**
     * Phase B. Asserts the upgraded build still knows an account exists.
     *
     * Landing on unlock rather than signup is the cheapest possible proof that `/data` survived the
     * in-place install. If preferences were wiped, this is where it shows.
     */
    @Test
    fun unlockScreenAppearsAfterUpgrade() {
        launchAppUnderTest(UNLOCK_HEADING)

        // Waits rather than querying once: the heading and the field are separate semantics nodes,
        // and a slow device can publish them in different frames. A one-shot `findObject` here
        // would report "no password field" for a screen that was merely a frame behind.
        assertNotNull(
            "unlock screen has no '$UNLOCK_PASSWORD_LABEL' field${ui.describeScreen()}",
            ui.findOrNull(By.text(UNLOCK_PASSWORD_LABEL)),
        )
    }

    private fun signUp() {
        ui.textField(SIGNUP_PASSWORD_LABEL).text = MASTER_PASSWORD
        ui.textField(SIGNUP_HINT_LABEL).text = PASSWORD_HINT
        ui.awaitObject(By.text(SIGNUP_BUTTON).enabled(true)).click()

        // Leaving the signup heading behind is the only "signed up" signal available without
        // asserting on home-screen content, which belongs to MR2.
        check(ui.awaitGone(By.text(SIGNUP_HEADING), SIGN_UP_TIMEOUT_MS)) {
            "still on the signup screen after tapping '$SIGNUP_BUTTON'${ui.describeScreen()}"
        }
    }

    /**
     * Restores the golden backup the host pushed to Downloads.
     *
     * Entry is through the empty vault's own "Restore data" shortcut rather than the Backup &
     * Restore tab: it is one tap instead of two, and it only exists while the vault is empty, so it
     * doubles as a check that sign-up really did leave an empty vault.
     *
     * The fixture's file name arrives as an instrumentation argument instead of being duplicated
     * here, because the host is what puts the file on the device — two copies of that name would
     * eventually disagree, and the failure would look like a picker bug.
     */
    private fun restoreGoldenBackup() {
        val fixtureFile = requiredArgument(FIXTURE_ARGUMENT)

        ui.awaitText(RESTORE_DATA_BUTTON).click()
        SafDocumentPicker(ui).selectFromDownloads(fixtureFile)

        ui.awaitText(RESTORE_PROMPT)
        ui.textField(RESTORE_PASSWORD_LABEL).text = BACKUP_PASSWORD
        ui.awaitText(CONFIRM_BUTTON).click()

        // The restore runs through a WorkManager worker that decrypts and re-encrypts every record,
        // so it is allowed far longer than an ordinary UI transition.
        ui.awaitText(RESTORE_SUCCESS_MESSAGE, RESTORE_TIMEOUT_MS)
        ui.awaitText(OK_BUTTON).click()
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
     */
    private fun assertSeededRecordsPresent() {
        ui.awaitText(RECORDS_TAB).click()

        val expected = FIXTURE_RECORD_TITLES.map { "restored:$it" } +
            SeedRecord.ALL.map { "ui:${it.title}" }
        val missing = expected.filter { ui.scrollToText(it.substringAfter(':')) == null }
        check(missing.isEmpty()) {
            "${missing.size} of ${expected.size} seeded records are not listed: " +
                "$missing${ui.describeScreen()}"
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
     * running perfectly. Observed on a Pixel 8 API 35 emulator on 2026-09-22; CI's `aosp-atd` image
     * has nothing enrolled, so this would have been a flake that only ever reproduced locally.
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

        // Independent of the master password: a .bak is encrypted under a password chosen at export
        // time. This one is recorded in upgrade-test/src/main/assets/fixtures/README.md.
        const val BACKUP_PASSWORD = "Fixture@Backup1"

        const val SIGNUP_HEADING = "Welcome !"
        const val UNLOCK_HEADING = "Welcome Back !"
        const val SIGNUP_BUTTON = "Sign Up"

        // Mandatory signup fields are labelled by MandatoryLabelText, which appends a red asterisk
        // inside the same text node, so the accessibility text is "Password*" and not "Password".
        // The unlock screen uses a plain Text for its label, hence two different constants - and
        // usefully, they also tell the two screens apart.
        const val SIGNUP_PASSWORD_LABEL = "Password*"
        const val SIGNUP_HINT_LABEL = "Hint*"
        const val UNLOCK_PASSWORD_LABEL = "Password"

        // The restore dialog's own field. Spelled separately from UNLOCK_PASSWORD_LABEL despite
        // being the same string: they are different screens owned by different code, and collapsing
        // them would make a rename of either look safe when it is not.
        const val RESTORE_PASSWORD_LABEL = "Password"

        const val RESTORE_DATA_BUTTON = "Restore data"
        const val RESTORE_PROMPT = "Please enter the password that was used to make the backup file."
        const val CONFIRM_BUTTON = "Confirm"
        const val RESTORE_SUCCESS_MESSAGE = "Data has been successfully restored."
        const val OK_BUTTON = "OK"
        const val RECORDS_TAB = "Records"

        const val FIXTURE_ARGUMENT = "fixtureFile"

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
