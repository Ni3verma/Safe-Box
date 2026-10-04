package com.andryoga.safebox.upgradetest.store

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import com.andryoga.safebox.upgradetest.BackupMaker
import com.andryoga.safebox.upgradetest.SafeBoxApp
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.BACKUP_DIR_ARGUMENT
import com.andryoga.safebox.upgradetest.SafeBoxApp.Companion.FIXED_PASSWORD
import com.andryoga.safebox.upgradetest.SettingsChanger
import com.andryoga.safebox.upgradetest.UiSupport
import com.andryoga.safebox.upgradetest.logStep
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the app through the scenes the Play Store listing shows and writes one raw screen capture
 * per scene and appearance. `scripts/take-store-screenshots.sh` is the only intended caller; it
 * installs the app and this harness, pins the device (demo status bar, animations off, night mode
 * per pass) and pulls the captures for `scripts/store-screenshots/render.py` to composite.
 *
 * Two methods, one `am instrument` call each:
 *
 * 1. [setUpDemoVault] once per run: sign up, grant the backup folder, turn Privacy mode off (it sets
 *    `FLAG_SECURE`, which turns every capture black), create [DemoVault] through the add flow and
 *    take one real backup so the backup screen shows a date.
 * 2. [captureScenes] once per appearance. The host flips night mode and force-stops the app between
 *    the two, so each pass is a cold start: the biometric sheet is dismissed, the password screen
 *    photographed, then every requested scene after unlocking. The biometric sheet itself is the
 *    one capture the host takes, through the emulator console, because it is a `FLAG_SECURE`
 *    window that `screencap` renders black.
 *
 * Nothing here depends on test tags: a release APK exposes none, and the tour must photograph it.
 *
 * Instrumentation arguments: `appPackage`, `backupDir` (setup), `theme`, `scenes`, `captureDir`
 * and the optional `variant` (capture).
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTour {

    private lateinit var safeBox: SafeBoxApp
    private lateinit var editor: RecordEditor
    private val ui: UiSupport get() = safeBox.ui

    @Before
    fun setUp() {
        safeBox = SafeBoxApp(SafeBoxApp.requiredArgument(APP_PACKAGE_ARGUMENT))
        editor = RecordEditor(safeBox.ui, safeBox.app)
    }

    /**
     * Fresh install to photographable vault: sign up, backup folder, Privacy mode off, demo
     * records, one backup. Auto-backup stays on so the settings screen keeps its shipped defaults
     * apart from the one row that has to move.
     */
    @Test
    fun setUpDemoVault() {
        safeBox.launchToSignUp()
        safeBox.signUp()
        safeBox.setBackupLocation(safeBox.argument(BACKUP_DIR_ARGUMENT))
        SettingsChanger(ui, safeBox.app).run {
            openSettingsTab()
            turnOff(PRIVACY_MODE)
        }
        ui.clickText(label(RECORDS_TAB))
        DemoVault.records.forEach(editor::add)
        backUp()
    }

    /**
     * Photographs the requested scenes in the current appearance, starting from a cold start.
     *
     * The optional `variant` argument is appended to every capture's file name; the host sets it
     * to the Material You seed when it photographs the password screen under other palettes
     * ([Scene.PALETTE]).
     */
    @Test
    fun captureScenes() {
        val theme = safeBox.argument(THEME_ARGUMENT)
        check(theme in THEMES) { "theme must be one of $THEMES, got '$theme'" }
        val scenes = Scene.parse(safeBox.argument(SCENES_ARGUMENT))
        val variant = InstrumentationRegistry.getArguments().getString(VARIANT_ARGUMENT)
        check(variant == null || VARIANT_PATTERN.matches(variant)) {
            "variant must be letters, digits or underscores, got '$variant'"
        }
        assertNightMode(theme)
        val shot = SceneCapture(safeBox.device, safeBox.argument(CAPTURE_DIR_ARGUMENT), theme, variant)
        logStep("capturing $theme${variant?.let { " ($it)" }.orEmpty()}: ${scenes.map { it.id }}")

        coldStartToUnlock()
        if (Scene.UNLOCK in scenes) shot.capture(Captures.UNLOCK_PASSWORD)
        if (scenes.none { it.behindUnlock }) return
        safeBox.unlock()
        ui.awaitText(DemoVault.firstTitle)
        if (Scene.RECORDS in scenes) {
            awaitTotpSecondsLeft(FRESH_PERIOD_S)
            shot.capture(Captures.RECORDS)
        }
        if (Scene.AUTHENTICATORS in scenes) captureAuthenticators(shot)
        if (Scene.SEARCH in scenes) captureSearch(shot)
        if (Scene.LOGIN_DETAIL in scenes) captureLoginDetail(shot)
        if (Scene.ADD in scenes) captureAdd(shot)
        if (Scene.BACKUP in scenes) captureBackup(shot)
        if (Scene.SETTINGS in scenes) captureSettings(shot)
    }

    /**
     * Holds a capture that shows authenticator rows until their codes have at least [minSeconds]
     * of the 30 s period left.
     *
     * The countdown ring turns the error colour in its last five seconds and the code changes at
     * zero, so a capture at an arbitrary moment shows a red ring one time in six (the first full
     * run did). A Play image has no root, so the clock cannot be frozen; the moment is chosen
     * instead. Three scenes show rings: the records list, the authenticator filter and the add
     * sheet, whose list shows through. The instrumentation shares the app's clock and the demo
     * authenticators use the default period.
     */
    private fun awaitTotpSecondsLeft(minSeconds: Int) {
        val left = TOTP_PERIOD_S - (System.currentTimeMillis() / MS_PER_S % TOTP_PERIOD_S).toInt()
        if (left >= minSeconds) return
        logStep("waiting ${left}s for the authenticator codes to roll over")
        SystemClock.sleep(left * MS_PER_S + ROLLOVER_SETTLE_MS)
    }

    /**
     * Starts the app cold and gets it to the password screen past the biometric sheet.
     *
     * The sheet itself is not photographed here: it is a `FLAG_SECURE` window, so `screencap`
     * returns an all-black frame while it is up. The host grabs it through the emulator console
     * instead, which reads the emulator's framebuffer and is not subject to the flag.
     *
     * While the sheet is up, SystemUI's window is the only one the accessibility tree reports, so
     * the sheet is found by its own title rather than waiting on the app. Backing out of it may
     * send the task home on gesture navigation; [SafeBoxApp.launchToUnlock] relaunches in that
     * case, and the sheet does not come back because the app offers biometric unlock once per
     * process. No sheet within the timeout (no fingerprint enrolled) is not an error here.
     */
    private fun coldStartToUnlock() {
        logStep("cold start, waiting for the biometric sheet")
        InstrumentationRegistry.getInstrumentation().context.startActivity(safeBox.launchIntent())
        val sheetTitle = By.pkg(SYSTEM_UI_PACKAGE).text(label(BIOMETRIC_TITLE))
        if (ui.findOrNull(sheetTitle, SHEET_TIMEOUT_MS) != null) {
            logStep("dismissing the biometric sheet")
            safeBox.device.pressBack()
            check(ui.awaitGone(sheetTitle)) {
                "the biometric sheet is still showing after back${ui.describeScreen()}"
            }
        }
        safeBox.launchToUnlock()
    }

    /**
     * The list filtered to authenticators, which shows live codes and countdown rings. The chip is
     * the topmost node carrying the type's text; the same text is also every authenticator row's
     * badge, below it.
     */
    private fun captureAuthenticators(shot: SceneCapture) {
        val chipText = label(DemoVault.RecordType.AUTHENTICATOR.sheetItemResource)
        val aLogin = By.text(DemoVault.firstTitle)
        tapTopmost(chipText)
        check(ui.awaitGone(aLogin)) {
            "'${DemoVault.firstTitle}' is still listed after filtering to '$chipText'" +
                ui.describeScreen()
        }
        awaitTotpSecondsLeft(RING_VISIBLE_PERIOD_S)
        shot.capture(Captures.AUTHENTICATORS)
        tapTopmost(chipText)
        ui.awaitObject(aLogin)
    }

    /** Search with the keyboard up and the list narrowed to [DemoVault.SEARCH_HITS]. */
    private fun captureSearch(shot: SceneCapture) {
        search(DemoVault.SEARCH_QUERY)
        ui.awaitCondition(
            UiSupport.FIND_TIMEOUT_MS,
            { "searching '${DemoVault.SEARCH_QUERY}' did not narrow the list to ${DemoVault.SEARCH_HITS}" },
        ) {
            DemoVault.SEARCH_HITS.all { ui.isPresent(By.text(it)) } &&
                !ui.isPresent(By.text(DemoVault.SEARCH_MISS))
        }
        shot.capture(Captures.SEARCH)
        clearSearch()
    }

    /**
     * A login's detail screen, reached through search so the row is on screen whatever the list's
     * length. The title is matched as a plain text node, because the search field holds the same
     * text while the filter is active.
     */
    private fun captureLoginDetail(shot: SceneCapture) {
        search(DemoVault.OPENED_LOGIN)
        ui.clickObject(By.text(DemoVault.OPENED_LOGIN).clazz(TEXT_VIEW_CLASS))
        ui.awaitObject(By.desc(label(EDIT_ACTION)), DETAIL_TIMEOUT_MS)
        shot.capture(Captures.LOGIN_DETAIL)
        safeBox.device.pressBack()
        ui.awaitObject(addButton())
        clearSearch()
    }

    /**
     * The type sheet, then a login form with [DemoVault.UNSAVED_LOGIN] typed in. The keyboard is
     * dismissed first so every field is in the picture; the form is abandoned with back rather
     * than saved, so the vault stays as [setUpDemoVault] left it.
     */
    private fun captureAdd(shot: SceneCapture) {
        editor.openAddSheet()
        awaitTotpSecondsLeft(RING_VISIBLE_PERIOD_S)
        shot.capture(Captures.ADD_SHEET)
        editor.pickType(DemoVault.UNSAVED_LOGIN.type)
        editor.fill(DemoVault.UNSAVED_LOGIN.fields)
        ui.hideKeyboard()
        shot.capture(Captures.ADD_FORM)
        leaveForm()
    }

    private fun captureBackup(shot: SceneCapture) {
        ui.clickText(label(BACKUP_TAB))
        ui.awaitText(label(BACKUP_SET_MESSAGE))
        shot.capture(Captures.BACKUP)
    }

    private fun captureSettings(shot: SceneCapture) {
        SettingsChanger(ui, safeBox.app).openSettingsTab()
        ui.awaitText(label(PRIVACY_MODE))
        shot.capture(Captures.SETTINGS)
    }

    /**
     * Takes a backup through the Backup & Restore tab so the backup screen shows a real "last
     * taken" date. Same dialog steps as [BackupMaker], but the button is found without its test tag
     * ([UiSupport.clickButton]) so a release APK works too.
     */
    private fun backUp() {
        logStep("back up so the backup screen shows a date")
        ui.clickText(label(BACKUP_TAB))
        ui.awaitText(label(BACKUP_SET_MESSAGE))
        ui.clickButton(label(BACKUP_BUTTON))
        ui.awaitText(label(BACKUP_PROMPT))
        ui.typeInto(label(PASSWORD_LABEL), FIXED_PASSWORD)
        ui.clickText(label(CONFIRM_BUTTON))
        ui.awaitText(label(BACKUP_SUCCESS_MESSAGE), BACKUP_TIMEOUT_MS)
        ui.clickText(label(OK_BUTTON))
    }

    /**
     * Focuses the search field, waits for the keyboard and sets the query.
     *
     * The keyboard is waited for before the text goes in, not just before the capture: Gboard
     * shows its suggestion strip when text arrives while it is up, and its toolbar when it comes
     * up after the text is already there. Setting the query first gave a light pass with the
     * toolbar and a dark pass with suggestions (2026-10-04).
     */
    private fun search(query: String) {
        logStep("search '$query'")
        val field = By.clazz(UiSupport.EDIT_TEXT_CLASS)
        ui.clickObject(field)
        ui.awaitKeyboard()
        ui.retryingOnStale { ui.awaitObject(field).text = query }
    }

    /**
     * Clears the search through its clear action, which also hides the keyboard and drops focus,
     * then waits for an unfiltered row to come back. Skipped if there is nothing to clear.
     */
    private fun clearSearch() {
        val clear = By.desc(label(CLEAR_SEARCH))
        if (ui.isPresent(clear)) ui.clickObject(clear)
        ui.awaitText(DemoVault.SEARCH_MISS)
    }

    /**
     * Backs out of a form to the records screen. The first back may only close the keyboard, so
     * up to two presses are spent, each checked.
     */
    private fun leaveForm() {
        repeat(FORM_BACK_PRESSES) {
            safeBox.device.pressBack()
            if (ui.findOrNull(addButton(), BACK_SETTLE_MS) != null) return
        }
        error("not back on the records screen after $FORM_BACK_PRESSES back presses${ui.describeScreen()}")
    }

    /** Taps the highest node on screen carrying [text]. */
    private fun tapTopmost(text: String) {
        logStep("tap topmost '$text'")
        ui.awaitText(text)
        ui.retryingOnStale {
            ui.findAll(By.text(text)).minBy { it.visibleBounds.top }.click()
        }
    }

    /**
     * Fails fast if the host did not put the device in the appearance this pass is labelled with,
     * which would file a dark capture under a light name.
     */
    private fun assertNightMode(theme: String) {
        val report = safeBox.device.executeShellCommand("cmd uimode night").trim()
        val expected = if (theme == DARK) "yes" else "no"
        check(report.endsWith(expected)) {
            "this pass is '$theme' but the device reports '$report'; the host sets night mode " +
                "before each pass"
        }
    }

    private fun addButton(): BySelector = By.desc(label(ADD_BUTTON))

    private fun label(resourceName: String): String = safeBox.app.label(resourceName)

    private companion object {
        // Instrumentation arguments; see scripts/take-store-screenshots.sh.
        const val APP_PACKAGE_ARGUMENT = "appPackage"
        const val THEME_ARGUMENT = "theme"
        const val SCENES_ARGUMENT = "scenes"
        const val CAPTURE_DIR_ARGUMENT = "captureDir"
        const val VARIANT_ARGUMENT = "variant"
        val VARIANT_PATTERN = Regex("[A-Za-z0-9_]+")

        const val LIGHT = "light"
        const val DARK = "dark"
        val THEMES = setOf(LIGHT, DARK)

        // The biometric sheet is SystemUI's window, not the app's.
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val TEXT_VIEW_CLASS = "android.widget.TextView"

        // Resource names, resolved against the installed build. See ADR-0003.
        const val RECORDS_TAB = "bottom_nav_records"
        const val BACKUP_TAB = "bottom_nav_backup_and_restore"
        const val PRIVACY_MODE = "settings_privacy_enabled_title"
        const val BIOMETRIC_TITLE = "biometric_title_text"
        const val ADD_BUTTON = "cd_add_new_record_button"
        const val CLEAR_SEARCH = "cd_clear_search_bar"
        const val EDIT_ACTION = "cd_action_edit"
        const val BACKUP_SET_MESSAGE = "backup_set_message"
        const val BACKUP_BUTTON = "backup"
        const val BACKUP_PROMPT = "new_backup_dialog_body_text"
        const val PASSWORD_LABEL = "password"
        const val CONFIRM_BUTTON = "confirm"
        const val BACKUP_SUCCESS_MESSAGE = "backup_complete_message"
        const val OK_BUTTON = "common_ok"

        const val SHEET_TIMEOUT_MS = 10_000L
        const val DETAIL_TIMEOUT_MS = 10_000L
        const val BACKUP_TIMEOUT_MS = 60_000L
        const val BACK_SETTLE_MS = 3_000L
        const val FORM_BACK_PRESSES = 2

        // Authenticator timing, see awaitTotpSecondsLeft. The records list is the first ring
        // capture of a pass and starts a period nearly full; the later ones only have to stay
        // clear of the last five seconds, which they do without waiting most of the time.
        const val TOTP_PERIOD_S = 30
        const val MS_PER_S = 1_000L
        const val FRESH_PERIOD_S = 24
        const val RING_VISIBLE_PERIOD_S = 10
        const val ROLLOVER_SETTLE_MS = 500L
    }
}
