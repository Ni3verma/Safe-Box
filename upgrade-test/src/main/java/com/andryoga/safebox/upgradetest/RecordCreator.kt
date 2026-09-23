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

    companion object {
        const val TITLE_LABEL = "Title*"
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
                    "Expiry Date" to "12/30",
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
     * Brings the records list to the front.
     *
     * Dismissing the restore dialog leaves the app on the Backup & Restore tab, not on the records
     * list, so the add button this flow starts from is not on screen at all. The tab is only tapped
     * when that button is missing, so callers do not have to know where the preceding step left the
     * app.
     */
    private fun openRecordsList() {
        if (ui.isPresent(By.desc(ADD_RECORD_FAB))) return
        ui.awaitText(RECORDS_TAB).click()
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
        ui.awaitObject(By.desc(ADD_RECORD_FAB)).click()
        ui.awaitText(ADD_RECORD_SHEET_TITLE)
        ui.awaitText(record.type).click()

        record.fields.forEach { (label, value) ->
            checkNotNull(ui.scrollToText(label)) {
                "the ${record.type} form has no '$label' field. The forms belong to the baseline " +
                    "release, so this list has to match what that build shipped, not this " +
                    "branch${ui.describeScreen()}"
            }
            ui.textField(label).text = value
        }

        ui.awaitText(SAVE_BUTTON).click()

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
