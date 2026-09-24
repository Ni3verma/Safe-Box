package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By

/**
 * Drives the unlock screen: reading the password hint, and submitting a password.
 *
 * These are the Group 1 and Group 2 checks of the plan. The master password hash and the hint live
 * in Room's `user_details` table, and the hint is encrypted with the same `symmetricDataKey` as the
 * records. Reading it is therefore the earliest point at which a lost key shows: the app crashes
 * with `AEADBadTagException` on Show Hint, before the vault is ever opened. The password check is
 * a hash comparison and does not involve that key, so it is checked on its own terms.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class UnlockScreen(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Reveals the password hint and returns it.
     *
     * The hint is a bare text node with no label and no content description, and its value is
     * the very thing being checked, so it cannot be looked up by text. It is identified as the one
     * text that appears when the hint is revealed: everything on screen after the tap, minus
     * everything before it, minus the toggle's own new caption, is the hint. That holds for any hint
     * that is not already written somewhere else on the screen.
     *
     * Both snapshots are limited to the app's own nodes. The status-bar clock is in the same
     * hierarchy, and a minute turning between the two snapshots made it a second "revealed" text in
     * run 3 of the first MR4 ten-run check.
     *
     * @return the hint as the app renders it
     */
    fun readHint(): String {
        val hideCaption = app.label(HIDE_HINT_BUTTON)
        val before = appTexts()
        ui.clickText(app.label(SHOW_HINT_BUTTON))
        ui.awaitText(hideCaption)

        val revealed = appTexts() - before - hideCaption
        check(revealed.size == 1) {
            "expected tapping '${app.label(SHOW_HINT_BUTTON)}' to reveal exactly one new text, " +
                "found $revealed${ui.describeScreen()}"
        }
        return revealed.single()
    }

    /**
     * Submits a password that must be refused, and proves it was refused the normal way.
     *
     * This guards the catastrophic inverse of a lost hash: a changed hashing scheme, or a
     * comparison that no longer compares, which lets *every* password in. Nothing else in the
     * harness would notice, because every other step uses the right password.
     *
     * @param password a password that is not the master password
     */
    fun assertRejects(password: String) {
        ui.typeInto(passwordLabel(), password)
        ui.clickObject(By.text(app.label(LOGIN_BUTTON)).enabled(true))
        ui.awaitText(app.label(INCORRECT_PASSWORD_MESSAGE))
        check(ui.isPresent(By.text(app.label(UNLOCK_HEADING)))) {
            "the app showed its wrong-password message but left the unlock screen" +
                ui.describeScreen()
        }
    }

    /**
     * Submits the master password and waits for the vault to open.
     *
     * [UiSupport.retypeMasked] rather than [UiSupport.typeInto]: after [assertRejects] the field
     * already holds text, so "the field is no longer empty" would be true before the app has taken
     * the new value, and the login tap could submit the rejected password a second time.
     *
     * @param password the master password
     */
    fun unlock(password: String) {
        ui.retypeMasked(passwordLabel(), password)
        ui.clickObject(By.text(app.label(LOGIN_BUTTON)).enabled(true))
        check(ui.awaitGone(By.text(app.label(UNLOCK_HEADING)), UNLOCK_TIMEOUT_MS)) {
            "still on the unlock screen after submitting the master password${ui.describeScreen()}"
        }
        ui.awaitText(app.label(RECORDS_TAB))
    }

    // The unlock screen labels its field with a plain Text, unlike signup's MandatoryLabelText, so
    // the plain label is the right one here.
    private fun passwordLabel() = app.label(PASSWORD_LABEL)

    private fun appTexts(): Set<String> =
        ui.textSnapshot(app.packageName).map { it.text }.toSet()

    private companion object {
        // Resource names, resolved against the installed build. See ADR-0003.
        const val UNLOCK_HEADING = "welcome_back"
        const val PASSWORD_LABEL = "password"
        const val SHOW_HINT_BUTTON = "show_hint"
        const val HIDE_HINT_BUTTON = "hide_hint"
        const val LOGIN_BUTTON = "login"
        const val INCORRECT_PASSWORD_MESSAGE = "incorrect_pswrd_message"
        const val RECORDS_TAB = "bottom_nav_records"

        // Unlocking hashes the password and then composes the records screen, the first to decrypt
        // anything in bulk, so it is allowed more than an ordinary screen transition.
        const val UNLOCK_TIMEOUT_MS = 15_000L
    }
}
