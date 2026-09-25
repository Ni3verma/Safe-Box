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
 * @param value the text to type, fixed so Phase A stays byte-reproducible. Empty means the field
 * is deliberately left blank: the form must still offer it, and the detail screen must then *not*
 * render it, which is what both apps do with a blank optional field
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
 * against it. The one exception is [UNICODE_LOGIN]'s URL, which is empty *on purpose*.
 *
 * The values are fixed on purpose: this is the *determinism* fixture, and Phase A has to produce
 * byte-identical results run after run. All but one are ASCII. [UNICODE_LOGIN] is plan step 8
 * (docs/testing/upgrade-testing.md section 5): a long field of emoji, right-to-left script and
 * combining marks, plus one empty optional field, created on the baseline and read back after the
 * upgrade. Those are the inputs where multi-byte UTF-8 meets the cipher's padding and the GCM tag.
 * Being fixed, it is exactly as reproducible as the ASCII records. The *adversarial* fixture
 * (`v3_adversarial.bak`: invalid seeds, 4000-character fields) belongs to MR6, restored into a
 * fresh install.
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
        get() = valueOf(TITLE)

    /**
     * The value seeded into one field.
     *
     * @param resourceName the field's label resource
     * @return the value typed into it
     */
    fun valueOf(resourceName: String): String =
        fields.first { it.resourceName == resourceName }.value

    companion object {
        const val TITLE = "title"
        const val SECRET_KEY = "secret_key"
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
         * One pass over everything step 8 asks for, repeated to about 500 characters.
         *
         * Invisible code points are escaped so a reader can see them: U+200D joins two emoji into
         * one "technologist" glyph, and U+0301 / U+0303 / U+0308 are combining marks written
         * *decomposed*, so a normalising layer anywhere in the save or read path shows up as a
         * changed value. Hebrew and Arabic put two right-to-left runs inside a left-to-right field.
         * There is no newline: `VaultOracle.fieldLine` would escape one, but multi-line notes are
         * already read field by field from the v1 fixture in Phase D, and adding one here would
         * change the oracle.
         */
        private const val UNICODE_SEGMENT =
            "Grüße 😀 👩\u200D💻 שלום עולם مرحبا بالعالم e\u0301 n\u0303 a\u0308 "
        private const val UNICODE_REPEATS = 10

        /**
         * Plan step 8: a long unicode field and an empty optional one, on the baseline.
         *
         * A login because its URL is optional and sits between two mandatory fields, so leaving it
         * blank exercises an empty column in the middle of a row rather than at its end. The long
         * text goes in the notes, which are unbounded in both builds; a field with a `maxLength`
         * drops an over-long value entirely rather than truncating it (see the card's expiry
         * date), and that would read as data loss here. Trimmed so no trailing space depends on how
         * a build saves text.
         */
        private val UNICODE_LOGIN = SeedRecord(
            typeResourceName = "type_display_login",
            fields = listOf(
                SeedField(TITLE, "${TITLE_PREFIX}ui unicode", isMandatory = true),
                SeedField("url", ""),
                SeedField("user_id", "unicode-user", isMandatory = true),
                SeedField("password", "UiUnicode@1"),
                SeedField(NOTES, UNICODE_SEGMENT.repeat(UNICODE_REPEATS).trimEnd()),
            ),
        )

        /**
         * The authenticator Phase C adds after the upgrade (plan Group 4), and whose code Group 5
         * checks. Not part of [ALL]: the baseline has no Authenticator type to create it with.
         *
         * The seed is the RFC 6238 Appendix B SHA-1 secret, the ASCII bytes `12345678901234567890`
         * in Base32. A published secret is the point: [Rfc6238] checks itself against the RFC's
         * own answers for it before it is trusted to judge the app. Digits, period and algorithm
         * are the app's defaults for a hand-entered key, which are also the RFC's.
         */
        val AUTHENTICATOR = SeedRecord(
            typeResourceName = "type_display_authenticator",
            fields = listOf(
                SeedField(TITLE, "${TITLE_PREFIX}ui totp", isMandatory = true),
                SeedField(SECRET_KEY, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", isMandatory = true),
            ),
        )

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
            UNICODE_LOGIN,
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
     * later fields are simply not reachable without scrolling. A field seeded empty is still
     * scrolled to, so the form is proven to offer it, and is then left blank.
     *
     * An authenticator does not open a form directly: the type row opens the QR scanner, and the
     * form is behind its manual-entry exit. The host grants the camera permission before the phase
     * that gets here, so no system permission dialog covers that button.
     *
     * @param record the record to create
     */
    fun create(record: SeedRecord) {
        val type = app.label(record.typeResourceName)
        openRecordsList()
        ui.clickObject(By.desc(app.label(ADD_RECORD_FAB)))
        ui.awaitText(app.label(ADD_RECORD_SHEET_TITLE))
        checkNotNull(ui.findOrNull(By.text(type))) {
            "the add-record sheet does not offer '$type' (resource '${record.typeResourceName}'), " +
                "so this build cannot create that type at all${ui.describeScreen()}"
        }
        ui.clickText(type)
        if (record.typeResourceName == AUTHENTICATOR_TYPE) {
            ui.clickText(app.label(ENTER_KEY_MANUALLY_BUTTON))
        }

        record.fields.forEach { field ->
            val label = app.formLabel(field.resourceName, field.isMandatory)
            checkNotNull(ui.scrollToText(label)) {
                "the $type form has no '$label' field (resource '${field.resourceName}'). The " +
                    "form belongs to whichever build is installed - the baseline in Phase A, the " +
                    "build under test afterwards - so this list has to match what that build " +
                    "ships, not this branch${ui.describeScreen()}"
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
        const val AUTHENTICATOR_TYPE = "type_display_authenticator"
        const val ENTER_KEY_MANUALLY_BUTTON = "enter_key_manually"
    }
}
