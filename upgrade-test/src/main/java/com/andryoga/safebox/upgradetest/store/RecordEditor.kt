package com.andryoga.safebox.upgradetest.store

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import com.andryoga.safebox.upgradetest.AppStrings
import com.andryoga.safebox.upgradetest.UiSupport
import com.andryoga.safebox.upgradetest.logStep
import java.util.regex.Pattern

/**
 * Creates records through the app's add flow: the `+` action on the records screen, the type
 * sheet, the form, Save.
 *
 * Everything is found by label and geometry, never by test tag, so the same steps drive a QA APK
 * and a release APK, whose tags are not exposed as resource ids. Two lookups need care:
 *
 * - **Type names repeat.** "Login" is the text of a filter chip, of every login row's badge and of
 *   the sheet item, so the sheet item is picked as the match that sits below the sheet's heading.
 * - **Form labels are not the plain resource.** A mandatory field renders its label as the
 *   resource text followed by a superscript `*` in the same node, and `title` is `"Title "` with a
 *   trailing space, so the node reads `Title *`. Labels are matched by a pattern that allows the
 *   optional whitespace and marker, see [formLabel].
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class RecordEditor(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Creates [record] and waits until the app is back on the records screen.
     *
     * The new row is not waited for: the list is sorted by title, so a record can land below the
     * fold, and the scene captures show the list anyway.
     *
     * @param record what to create
     */
    fun add(record: DemoVault.Record) {
        logStep("add ${record.type} '${record.title}'")
        openForm(record.type)
        fill(record.fields)
        val save = app.label(SAVE)
        ui.clickText(save)
        check(ui.awaitGone(By.text(save), SAVE_TIMEOUT_MS)) {
            "still on the form after tapping '$save' for '${record.title}'${ui.describeScreen()}"
        }
        ui.awaitObject(By.desc(app.label(ADD_BUTTON)))
    }

    /**
     * Opens the type sheet from the records screen and waits for its heading.
     */
    fun openAddSheet() {
        ui.clickObject(By.desc(app.label(ADD_BUTTON)))
        ui.awaitText(app.label(SHEET_HEADING))
    }

    /**
     * Opens an empty form for [type]: [openAddSheet] then [pickType].
     *
     * @param type the record type to add
     */
    fun openForm(type: DemoVault.RecordType) {
        openAddSheet()
        pickType(type)
    }

    /**
     * Taps [type]'s item in the open sheet and waits for the form's Save action. An authenticator
     * goes through the QR scanner first, so its manual-entry button is tapped on the way.
     *
     * The camera permission must already be granted, or the scanner shows a rationale dialog
     * instead of the manual-entry button; the host grants it before the run.
     *
     * The item is the match for the type's text that sits below the sheet heading, which excludes
     * the filter chip and the row badges above it.
     *
     * @param type the record type to add
     */
    fun pickType(type: DemoVault.RecordType) {
        val heading = app.label(SHEET_HEADING)
        val item = app.label(type.sheetItemResource)
        logStep("choose '$item' in the add sheet")
        ui.retryingOnStale {
            val headingBottom = ui.awaitText(heading).visibleBounds.bottom
            val candidates = ui.findAll(By.text(item))
            candidates.filter { it.visibleBounds.top >= headingBottom }
                .minByOrNull { it.visibleBounds.top }
                ?.click()
                ?: error(
                    "no '$item' below the '$heading' heading among ${candidates.size} matches" +
                        ui.describeScreen(),
                )
        }
        check(ui.awaitGone(By.text(heading))) {
            "the add sheet is still open after tapping '$item'${ui.describeScreen()}"
        }
        if (type == DemoVault.RecordType.AUTHENTICATOR) {
            ui.clickText(app.label(ENTER_KEY_MANUALLY))
        }
        ui.awaitText(app.label(SAVE))
    }

    /**
     * Types every field, scrolling the form to each label first: a long form (the bank account
     * has ten fields) places its last fields below the fold, and nodes outside the viewport are
     * not in the accessibility tree.
     *
     * @param fields values to type, in form order
     */
    fun fill(fields: List<DemoVault.Field>) {
        fields.forEach { field ->
            val label = app.label(field.labelResource)
            val selector = formLabel(label)
            ui.retryingOnStale { ui.scrollTo(selector) }
            ui.typeInto(selector, field.value, label)
        }
    }

    private companion object {
        // Resource names, resolved against the installed build. See ADR-0003.
        const val ADD_BUTTON = "cd_add_new_record_button"
        const val SHEET_HEADING = "add_a_new_record"
        const val ENTER_KEY_MANUALLY = "enter_key_manually"
        const val SAVE = "save"

        // Saving encrypts every field and returns to the list, which then re-reads the vault.
        const val SAVE_TIMEOUT_MS = 20_000L

        /**
         * Matches a form field's label node whether the field is mandatory or not.
         *
         * @param label the label's resource text, trimmed
         * @return a selector for `label`, `label*` or `label *` as a whole-text match
         */
        fun formLabel(label: String): BySelector =
            By.text(Pattern.compile(Pattern.quote(label) + "\\s*\\*?"))
    }
}
