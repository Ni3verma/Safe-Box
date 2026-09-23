package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By

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
 * is the job of `v3_adversarial.bak` in MR4, where the assertions that make it meaningful live.
 */
internal data class SeedRecord(
    /** Row label in the "Add a new record" sheet, which is also the form's title. */
    val type: String,
    /** Field label to value, in the order the form presents them. */
    val fields: List<Pair<String, String>>,
) {
    /** The record's title, which is how it is identified in the records list. */
    val title: String
        get() = fields.first { it.first == TITLE_LABEL }.second

    /**
     * The same fields as the detail screen labels them.
     *
     * The read-only screen drops the mandatory marker the forms add, and nothing else differs, so
     * this is derived rather than declared: a second hand-written list would be one rename away
     * from disagreeing with the one above, and the disagreement would look like a missing field.
     */
    val detailLabels: List<String>
        get() = fields.map { it.first.removeSuffix(MANDATORY_MARKER) }

    companion object {
        const val MANDATORY_MARKER = "*"
        const val TITLE_LABEL = "Title$MANDATORY_MARKER"
        private const val UI_NOTES = "created through the UI"

        /**
         * Field labels were read off the installed `v2.0.4.0` build, not from this branch's
         * sources — the baseline is a different release and is the thing being driven.
         */
        val ALL = listOf(
            SeedRecord(
                type = "Login",
                fields = listOf(
                    TITLE_LABEL to "ui login",
                    "URL" to "https://ui.example.com",
                    "User Id*" to "ui-user",
                    "Password" to "UiLogin@1",
                    "Notes" to UI_NOTES,
                ),
            ),
            SeedRecord(
                type = "Card",
                fields = listOf(
                    TITLE_LABEL to "ui card",
                    "Name" to "UI Card Holder",
                    "Number*" to "4111111111111111",
                    "PIN" to "1234",
                    "CVV" to "321",
                    // Four digits, no slash. The field caps input at four characters and the
                    // slash is added by a visual transformation, so "12/30" is not merely
                    // reformatted - RowField drops the whole value and the card saves with no
                    // expiry date, silently. Verified against v2.0.4.0 on 2026-09-23: the detail
                    // screen showed no Expiry Date row at all.
                    "Expiry Date" to "1230",
                    "Notes" to UI_NOTES,
                ),
            ),
            SeedRecord(
                type = "Bank Account",
                fields = listOf(
                    TITLE_LABEL to "ui bank",
                    "Account Number*" to "000111222333",
                    "Customer Name" to "UI Customer",
                    "Customer Id" to "CUST-1",
                    "Branch Code" to "BR1",
                    "Branch Name" to "UI Branch",
                    "Branch Address" to "1 UI Street",
                    "IFSC Code" to "UIFS0000001",
                    "MICR Code" to "110000001",
                    "Notes" to UI_NOTES,
                ),
            ),
            SeedRecord(
                type = "Note",
                fields = listOf(
                    TITLE_LABEL to "ui note",
                    "Notes*" to "note body created through the UI",
                ),
            ),
        )
    }
}

/**
 * Creates [SeedRecord]s through the add-record flow.
 *
 * @param ui shared waiting and failure-description plumbing
 */
internal class RecordCreator(private val ui: UiSupport) {

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
        if (ui.isPresent(By.desc(ADD_RECORD_FAB))) return
        ui.clickText(RECORDS_TAB)
        ui.scrollToTop()
        ui.awaitObject(By.desc(ADD_RECORD_FAB))
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
        openRecordsList()
        ui.clickObject(By.desc(ADD_RECORD_FAB))
        ui.awaitText(ADD_RECORD_SHEET_TITLE)
        ui.clickText(record.type)

        record.fields.forEach { (label, value) ->
            checkNotNull(ui.scrollToText(label)) {
                "the ${record.type} form has no '$label' field. The forms belong to the baseline " +
                    "release, so this list has to match what that build shipped, not this " +
                    "branch${ui.describeScreen()}"
            }
            ui.typeInto(label, value)
        }

        ui.clickText(SAVE_BUTTON)

        // The record's own row appearing in the list is the signal that the save was accepted: a
        // rejected form stays put with a validation error, and the next record's tap would then
        // land on the form still being displayed. The row is checked rather than the add button
        // because the row is what the save is supposed to produce.
        checkNotNull(ui.scrollToText(record.title)) {
            "saving the ${record.type} record '${record.title}' did not return to the records " +
                "list - the form is probably showing a validation error${ui.describeScreen()}"
        }
    }

    private companion object {
        const val ADD_RECORD_FAB = "Add new record button"
        const val ADD_RECORD_SHEET_TITLE = "Add a new record"
        const val RECORDS_TAB = "Records"
        const val SAVE_BUTTON = "Save"
    }
}
