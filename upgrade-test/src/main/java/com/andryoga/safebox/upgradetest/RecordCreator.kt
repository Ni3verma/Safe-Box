package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By

/**
 * One field of a seeded record, keyed by the app's resource name rather than by its label.
 *
 * Holding `user_id` rather than `"User Id"` is what lets the same declaration drive the baseline's
 * forms and be read back from a later build's detail screens — see
 * [ADR-0003](../../../../../../../../docs/decisions/0003-ui-labels-from-resource-names.md).
 *
 * @param resourceName the app string resource that labels this field
 * @param value the text to type, chosen to be fixed and ASCII so Phase A stays byte-reproducible
 * @param isMandatory whether the form marks the field mandatory, which changes the label it
 * renders but not the label the detail screen shows; see [AppStrings.formLabel]
 */
internal data class SeedField(
    val resourceName: String,
    val value: String,
    val isMandatory: Boolean = false,
)

/**
 * One record of each type, created through the app's own forms.
 *
 * This exists alongside the restored fixture rather than instead of it, because the two take
 * different routes into the database: a restore is written by the restore worker, while these go
 * through the repository as a user's own input. They are two distinct encryption call sites, and a
 * defect in either one is invisible if only the other is exercised.
 *
 * Every field is filled, not just the mandatory ones. An empty optional column survives any
 * migration trivially, so a half-filled record would quietly weaken every later assertion made
 * against it.
 *
 * The values are fixed and ASCII on purpose: this is the *determinism* fixture, and Phase A has to
 * produce byte-identical results run after run. Hostile input — emoji, RTL, 4000-character fields —
 * is the job of `v3_adversarial.bak` in MR5, where the assertions that make it meaningful live.
 *
 * @param typeResourceName the app string resource naming this record's type, which labels both the
 * row in the add-record sheet and the chip in the records list
 * @param fields every field the form offers, in the order it presents them
 */
internal data class SeedRecord(
    val typeResourceName: String,
    val fields: List<SeedField>,
) {
    /** The record's title, which is how it is identified in the records list. */
    val title: String
        get() = fields.first { it.resourceName == TITLE }.value

    companion object {
        const val TITLE = "title"
        private const val NOTES = "notes"
        private const val UI_NOTES = "created through the UI"

        // Sorts every UI-created record above the seven restored ones. The list is ordered by
        // title, case-insensitively, and "ui ..." used to sort last - below the fold - so each of
        // the twelve lookups against these titles (after save, in the presence check, and when the
        // oracle opens the detail screen) paid scrollToText's two-second look at the wrong screen
        // before it began to scroll. A leading digit sorts ahead of every letter. Nothing is
        // weakened by it: an unreadable final screenful is still caught by the oracle's
        // missing-record check, now against fixture rows instead of these.
        private const val TITLE_PREFIX = "0 "

        /**
         * Which fields each form offers, and which of them it marks mandatory, was read off the
         * installed `v2.0.4.0` build's layouts — the baseline is a different release and is the
         * thing being driven. The resource *names* below are stable across both builds, so only
         * the presence and order of fields is baseline-specific.
         */
        val ALL = listOf(
            SeedRecord(
                typeResourceName = "type_display_login",
                fields = listOf(
                    SeedField(TITLE, "${TITLE_PREFIX}ui login", isMandatory = true),
                    SeedField("url", "https://ui.example.com"),
                    SeedField("user_id", "ui-user", isMandatory = true),
                    SeedField("password", "UiLogin@1"),
                    SeedField(NOTES, UI_NOTES),
                ),
            ),
            SeedRecord(
                typeResourceName = "type_display_card",
                fields = listOf(
                    SeedField(TITLE, "${TITLE_PREFIX}ui card", isMandatory = true),
                    SeedField("name", "UI Card Holder"),
                    SeedField("number", "4111111111111111", isMandatory = true),
                    SeedField("pin", "1234"),
                    SeedField("cvv", "321"),
                    // Four digits, no slash. The field caps input at four characters and the
                    // slash is added by a visual transformation, so "12/30" is not merely
                    // reformatted - RowField drops the whole value and the card saves with no
                    // expiry date, silently. Verified against v2.0.4.0 on 2026-09-23: the detail
                    // screen showed no Expiry Date row at all.
                    SeedField("expiryDate", "1230"),
                    SeedField(NOTES, UI_NOTES),
                ),
            ),
            SeedRecord(
                typeResourceName = "type_display_account",
                fields = listOf(
                    SeedField(TITLE, "${TITLE_PREFIX}ui bank", isMandatory = true),
                    SeedField("account_number", "000111222333", isMandatory = true),
                    SeedField("customer_name", "UI Customer"),
                    SeedField("customer_id", "CUST-1"),
                    SeedField("branch_code", "BR1"),
                    SeedField("branch_name", "UI Branch"),
                    SeedField("branch_address", "1 UI Street"),
                    SeedField("ifsc_code", "UIFS0000001"),
                    SeedField("micr_code", "110000001"),
                    SeedField(NOTES, UI_NOTES),
                ),
            ),
            SeedRecord(
                typeResourceName = "type_display_note",
                fields = listOf(
                    SeedField(TITLE, "${TITLE_PREFIX}ui note", isMandatory = true),
                    SeedField(NOTES, "note body created through the UI", isMandatory = true),
                ),
            ),
        )
    }
}

/**
 * Creates [SeedRecord]s through the add-record flow.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class RecordCreator(private val ui: UiSupport, private val app: AppStrings) {

    /** Creates every canonical record, leaving the app on the records list. */
    fun createAll() = SeedRecord.ALL.forEach(::create)

    /**
     * Brings the records list to the front, at the top.
     *
     * Two different things can hide the add button, and both look identical from here. Dismissing
     * the restore dialog leaves the app on the Backup & Restore tab, where the button does not
     * exist; and the records list sits under a collapsing app bar, so once the list is scrolled —
     * which the previous record's save check leaves it — the button is gone from the hierarchy
     * even though the right screen is showing. Hence tab first, then rewind, then look.
     */
    private fun openRecordsList() {
        if (ui.isPresent(By.desc(app.label(ADD_RECORD_FAB)))) return
        ui.clickText(app.label(RECORDS_TAB))
        ui.scrollToTop()
        ui.awaitObject(By.desc(app.label(ADD_RECORD_FAB)))
    }

    /**
     * Creates one record: add button, type row, fields, save.
     *
     * Each label is scrolled to before being filled. The bank account form has ten fields and is
     * taller than a phone screen, so on a smaller device than the one this was written against the
     * later fields are simply not reachable without scrolling.
     *
     * @param record the record to create
     */
    fun create(record: SeedRecord) {
        val type = app.label(record.typeResourceName)
        openRecordsList()
        ui.clickObject(By.desc(app.label(ADD_RECORD_FAB)))
        ui.awaitText(app.label(ADD_RECORD_SHEET_TITLE))
        ui.clickText(type)

        record.fields.forEach { field ->
            val label = app.formLabel(field.resourceName, field.isMandatory)
            checkNotNull(ui.scrollToText(label)) {
                "the $type form has no '$label' field (resource '${field.resourceName}'). The " +
                    "forms belong to the baseline release, so this list has to match what that " +
                    "build shipped, not this branch${ui.describeScreen()}"
            }
            ui.typeInto(label, field.value)
        }

        val save = app.label(SAVE_BUTTON)
        ui.clickText(save)

        // The form has to be gone before the row is looked for, and that is not a formality: the
        // form's own Title field still holds `record.title`, so searching for the title while the
        // form is up matches the text that was just typed into it. The check would then pass on a
        // form that had refused to save, and the failure would surface one record later as a
        // baffling "could not find Records" timeout. The Save button exists only on the form.
        check(ui.awaitGone(By.text(save))) {
            "saving the $type record '${record.title}' left the form open - it is " +
                "probably showing a validation error${ui.describeScreen()}"
        }
        checkNotNull(ui.scrollToText(record.title)) {
            "saving the $type record '${record.title}' returned to the records list, " +
                "but no row with that title is listed${ui.describeScreen()}"
        }
    }

    private companion object {
        const val ADD_RECORD_FAB = "cd_add_new_record_button"
        const val ADD_RECORD_SHEET_TITLE = "add_a_new_record"
        const val RECORDS_TAB = "bottom_nav_records"
        const val SAVE_BUTTON = "save"
    }
}
