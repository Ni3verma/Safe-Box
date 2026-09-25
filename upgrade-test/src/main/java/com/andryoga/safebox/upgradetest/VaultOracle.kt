package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import java.util.regex.Pattern
import java.util.regex.Pattern.quote

/**
 * Reads the seeded vault back through the UI and writes down what it found.
 *
 * "The upgrade preserved the data" is not directly observable through a black box, so the harness
 * settles for something that is: a text file describing everything Phase A put there, captured the
 * same way a user would see it. Phase A's own acceptance is that ten consecutive runs produce the
 * same file byte for byte; later stages capture it again after the upgrade and diff the two.
 *
 * Four properties matter more than completeness, and shape everything below.
 *
 * **Every key is a resource name, never a visible label.** `field.0 ui login.user_id=ui-user`, not
 * `field.0 ui login.User Id=ui-user`. This file is captured from the *old* build and diffed against
 * one captured from the *new* one, so a key made of displayed text would turn every future copy
 * edit into what looks exactly like data loss — and the cheap way out of that is to loosen the
 * comparison, which is how the previous attempt at upgrade testing became worthless. The reasoning
 * is [ADR-0003](../../../../../../../../docs/decisions/0003-ui-labels-from-resource-names.md); the
 * labels themselves are resolved from whichever APK is installed, by [AppStrings].
 *
 * **Nothing time-varying may appear.** The detail screens carry `Created on` and `Updated on`
 * stamps and the backup screen carries "last taken on", all of which move every run. They are
 * excluded by *reading only named fields* rather than by dumping the screen and filtering
 * afterwards — a filter is a list of things somebody remembered, and the first forgotten one turns
 * the acceptance into noise that everybody learns to ignore.
 *
 * **A missing field must fail, not vanish.** Each field is looked up by name, so a value the app
 * stops rendering aborts the capture. A screen dump would instead have written a shorter file that
 * was perfectly reproducible — ten identical runs, all of them missing the same data. That is not
 * hypothetical: the card's expiry date was being dropped at save time and nobody noticed until a
 * detail screen was read by hand.
 *
 * **It records what is displayed, not what was typed.** Card numbers come back grouped into fours
 * and expiry dates come back with a slash, because visual transformations put them there. That is
 * the right level for this file: it is a snapshot to diff against itself, not an assertion about
 * storage.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 * @param expectedTitles the titles the calling phase expects — Phase A's seed, plus the
 * authenticator once Phase C has added it — used to reject a records list that is not the one this
 * file claims to describe
 * @param typeResourceNames the record types to recognise on the list. Defaults to the four every
 * build has; a list holding an authenticator must add [AUTHENTICATOR_TYPE], which the baseline
 * cannot resolve at all
 * @param detailRecords the records whose every field is read back. Defaults to the ones Phase A
 * creates through the UI; Phase D's legacy restore passes [LegacyFixture.RECORDS]
 */
internal class VaultOracle(
    private val ui: UiSupport,
    private val app: AppStrings,
    private val expectedTitles: Set<String>,
    private val typeResourceNames: List<String> = BASELINE_TYPE_RESOURCE_NAMES,
    private val detailRecords: List<SeedRecord> = SeedRecord.ALL,
) {

    /**
     * Each record type's displayed chip text, mapped back to the resource that produced it.
     *
     * Built once and in this direction because the screen only ever hands back text: a chip has to
     * be recognised as a type and then written to the oracle under a stable key, and that is a
     * lookup from label to name.
     *
     * Two type resources rendering the same text would collapse this map silently and drop a type
     * from the oracle, so the size is checked rather than assumed. It is not far-fetched: the
     * baseline already ships a separate `login` string whose text is also "Login", which is
     * precisely why this list names the `type_display_*` family explicitly.
     */
    private val typeResourceByLabel: Map<String, String> =
        typeResourceNames.associateBy { app.label(it) }.also { byLabel ->
            check(byLabel.size == typeResourceNames.size) {
                "two of $typeResourceNames render the same text in the installed build, so the " +
                    "oracle cannot tell them apart: $byLabel"
            }
        }

    /**
     * Walks the whole app and returns what it found, one `key=value` line per fact.
     *
     * Returned rather than written, because the vault is not the only thing a phase records: the
     * password hint lives on the unlock screen, which can only be reached by locking the app after
     * this walk. The caller assembles the full oracle and hands it to [OracleFiles].
     *
     * @return the vault's description, newline-terminated
     */
    fun describe(): String = buildString {
        ui.clickText(app.label(RECORDS_TAB))
        val rows = collectRows()
        appendLine("$RECORD_COUNT_KEY=${rows.size}")
        rows.values.groupingBy { it }.eachCount().toSortedMap().forEach { (type, count) ->
            appendLine("record.type.$type=$count")
        }
        rows.forEach { (title, type) -> appendLine("record.title.$title=$type") }

        detailRecords.forEach { record -> appendDetailOf(record) }
        appendBackupLocation()
        appendSettings()
    }

    /**
     * Every record in the list, as title to type resource name.
     *
     * A map keyed by title rather than a running count, because the list has to be walked in
     * screenfuls and the screenfuls overlap: re-reading a row that was already seen must not
     * inflate the total.
     *
     * That key is also a blind spot, and it is covered elsewhere rather than here. Two rows with
     * the same title collapse into one entry, so a record accidentally created twice would leave
     * `record.count` looking correct and would survive the ten-run acceptance, since it reproduces
     * perfectly. Duplicates are therefore rejected by [visibleRows], which sees the rows of a
     * single screenful before anything is deduplicated.
     *
     * The screenful is read *after* every scroll, including the scroll that reports it has reached
     * the end. Reading only while the container says it can scroll further loses the last
     * screenful, which is where the records seeded through the UI sorted at the time — Phase A
     * wrote a perfectly reproducible oracle that was missing four of eleven records before this was
     * fixed. They have since been prefixed to sort first, so today the last screenful holds
     * restored records; the rule matters exactly as much, and the missing-record check below is
     * still what catches a violation.
     */
    private fun collectRows(): Map<String, String> {
        val rows = sortedMapOf<String, String>()
        ui.scrollToTop()
        rows += visibleRows()
        var remaining = MAX_SCROLLS
        while (remaining-- > 0) {
            val more = ui.scrollList(Direction.DOWN)
            rows += visibleRows()
            if (!more) break
        }
        checkRowsAreExactlyWhatWasSeeded(rows)
        return rows
    }

    /**
     * Refuses to write an oracle describing a vault nobody asked for.
     *
     * The rest of this class is deliberately descriptive rather than assertive, but the row set is
     * the one place that cannot be: the rows are found by geometry, and geometry has no way of
     * telling a wrong answer from a right one. Only the *missing* direction is checked here, since
     * a record simply has not been reached yet until the walk is over; an *extra* one is caught
     * where it happens, by [visibleRows].
     *
     * @param rows what the walk found, as title to type resource name
     */
    private fun checkRowsAreExactlyWhatWasSeeded(rows: Map<String, String>) {
        val missing = expectedTitles - rows.keys
        check(missing.isEmpty()) {
            "the records list does not hold every record this phase expects. Missing: $missing" +
                ui.describeScreen()
        }
    }

    /**
     * Pairs the type chip on the right of each visible row with the title on its left.
     *
     * Geometry again: a row is not a node, so the only thing connecting a title to its type is that
     * they share a line. The title is the *topmost* node overlapping the chip, because the second
     * line of a row is a subtitle - a masked card number or a user id - which sits below the chip.
     *
     * The top of the list carries a *filter* row spelling out all four type names on one line, and
     * its rightmost entry sits right of centre, so position alone cannot tell a filter from a row's
     * chip: the filter row paired `Note` with `Login`, producing a twelfth record that does not
     * exist. What separates them is that a record row names exactly one type, so a type name
     * sharing its line with another type name is a filter and is dropped. The filter row is only
     * visible at the top of the list, which is why this surfaced only once the walk started
     * rewinding before reading.
     *
     * A pairing that produces a title Phase A never seeded fails *here*, on the screenful that
     * produced it, and prints that screenful's text with its positions. Catching it at the end of
     * the walk instead reports it against whatever the list happens to show when the walk stops,
     * which is a different frame and says nothing about what went wrong.
     *
     * The same screenful is also where a *duplicated* record is still visible. The walk's result is
     * keyed by title, which it has to be because screenfuls overlap, and that key destroys the
     * evidence: two rows called `0 ui login` become one entry and a count that looks right. Here the
     * rows are still a list, so the same title on two different lines can be rejected. The list is
     * sorted by title, so a duplicate pair is adjacent and lands on one screenful.
     *
     * @return the screenful's rows, as title to type *resource name* - the chip's text is resolved
     * back to the resource that produced it as early as possible, so no displayed label reaches
     * the oracle
     */
    private fun visibleRows(): Map<String, String> {
        val typeColumnLeft = ui.device.displayWidth / 2
        val tabLabels = TAB_RESOURCE_NAMES.map { app.label(it) }
        val screen = ui.textSnapshot().filterNot { it.text in tabLabels }
        val typeNames = screen.filter { it.text in typeResourceByLabel }

        val rows = typeNames
            .filter { chip ->
                chip.bounds.left > typeColumnLeft && typeNames.count { chip.sharesLineWith(it) } == 1
            }
            .mapNotNull { chip ->
                screen.filter { it.bounds.left < typeColumnLeft && chip.sharesLineWith(it) }
                    .minByOrNull { it.bounds.top }
                    ?.let { title -> title.text to typeResourceByLabel.getValue(chip.text) }
            }

        val duplicated = rows.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        check(duplicated.isEmpty()) {
            "one screenful of the records list shows $duplicated more than once, so the vault " +
                "holds a record that was created twice. That screenful read as:\n" +
                screen.joinToString("\n") { "${it.text} @ ${it.bounds.toShortString()}" }
        }

        val unexpected = rows.map { it.first }.toSet() - expectedTitles
        check(unexpected.isEmpty()) {
            "a screenful of the records list paired $unexpected with a type chip, and this phase " +
                "expects no record with that title. That screenful read as:\n" +
                screen.joinToString("\n") { "${it.text} @ ${it.bounds.toShortString()}" }
        }
        return rows.toMap()
    }

    /**
     * Opens one record and writes down every field it was seeded with.
     *
     * Both the arrival and the departure are keyed off the detail screen's own edit button rather
     * than off anything on the list. The list's add button is in a top app bar that scrolls away
     * with the content, so "the add button is back" is false whenever the list is not at the top —
     * which, after walking it to the bottom to count rows, it never is. The record's type is no
     * good either: every row in the list already displays it.
     *
     * The label looked for on screen and the key written to the file come from the same resource
     * name, resolved once. A second, hand-written list of detail-screen labels would be one rename
     * away from disagreeing with the seed data, and the disagreement would read as a missing field.
     * The read-only screen renders no mandatory marker, so [AppStrings.label] is right here and
     * [AppStrings.formLabel] is right in [RecordCreator].
     *
     * A field seeded empty is recorded as an empty value, after checking that the screen does not
     * render it: both builds hide a blank field in view mode, so its label turning up means the
     * column came back holding something. Those checks walk the whole screen, so they run after
     * every present field has been read, and the lines are still written in declaration order.
     *
     * @param record the record to read back
     */
    private fun StringBuilder.appendDetailOf(record: SeedRecord) {
        ui.scrollToTop()
        checkNotNull(ui.scrollToText(record.title)) {
            "'${record.title}' is not in the records list${ui.describeScreen()}"
        }
        ui.clickText(record.title)
        ui.awaitObject(By.desc(app.label(EDIT_RECORD_BUTTON)))

        val (blank, filled) = record.fields.partition { it.value.isEmpty() }
        val values = filled.associate { field ->
            field.resourceName to ui.valueBelow(app.label(field.resourceName))
        }
        blank.forEach { field ->
            val label = app.label(field.resourceName)
            check(ui.scrollToText(label, timeoutMs = 0) == null) {
                "'${record.title}' was saved with '$label' blank, yet its detail screen renders " +
                    "that field${ui.describeScreen()}"
            }
        }
        record.fields.forEach { field ->
            val value = values[field.resourceName].orEmpty()
            appendLine(fieldLine(record.title, field.resourceName, value))
        }

        ui.device.pressBack()
        check(ui.awaitGone(By.desc(app.label(EDIT_RECORD_BUTTON)))) {
            "still on the '${record.title}' detail screen after pressing back${ui.describeScreen()}"
        }
    }

    /**
     * Records whether the backup screen says a location is set.
     *
     * Only the *state* is recorded, not the path: the path arrives inside an app sentence
     * ("Backup path is …"), so recording it would put the app's wording into a value and turn a
     * future copy edit into what reads as lost data. The state is identified by which resource is
     * showing, so it survives any rewording. Phase A has already checked the exact directory at
     * the moment it granted it; after the upgrade the question is only whether the app still
     * remembers that one is set. Whether the platform grant behind it survived is a different
     * question, and only an actual backup can answer it.
     */
    private fun StringBuilder.appendBackupLocation() {
        ui.clickText(app.label(BACKUP_TAB))
        val isSet = app.label(BACKUP_LOCATION_SET_MESSAGE)
        val notSet = app.label(SET_LOCATION_BUTTON)
        val shown = ui.awaitObject(By.text(Pattern.compile("${quote(isSet)}|${quote(notSet)}")))
        val state = if (shown.text == isSet) "set" else "not_set"
        appendLine("backup.location=$state")
    }

    private fun StringBuilder.appendSettings() {
        ui.clickText(app.label(SETTINGS_TAB))
        SettingsChanger.OFF_BY_DEFAULT_AFTER_THIS.forEach { resourceName ->
            val label = app.label(resourceName)
            val state = if (ui.retryingOnStale { ui.switchBeside(label).isChecked }) "on" else "off"
            appendLine("setting.$resourceName=$state")
        }
    }

    companion object {
        private const val RECORD_COUNT_KEY = "record.count"
        private const val RECORD_TYPE_PREFIX = "record.type."
        private const val RECORD_TITLE_PREFIX = "record.title."
        private const val FIELD_PREFIX = "field."

        // Resource names, resolved against the installed build. See ADR-0003.
        private const val RECORDS_TAB = "bottom_nav_records"
        private const val BACKUP_TAB = "bottom_nav_backup_and_restore"
        private const val SETTINGS_TAB = "bottom_nav_settings"
        private const val EDIT_RECORD_BUTTON = "cd_action_edit"

        // The backup screen's two states: a set location shows this message, an unset one offers
        // this button. Exactly one of them is on screen once the tab has loaded.
        private const val BACKUP_LOCATION_SET_MESSAGE = "backup_set_message"
        private const val SET_LOCATION_BUTTON = "backup_set_location"

        // The four type chips the records list renders on the right of every row. Deliberately the
        // `type_display_*` family: the baseline also ships a plain `login` string reading "Login",
        // and pairing a chip with that one would key the oracle on a resource the list never uses.
        val BASELINE_TYPE_RESOURCE_NAMES = listOf(
            "type_display_login",
            "type_display_card",
            "type_display_account",
            "type_display_note",
        )

        // The fifth type, from DB 5 onwards. Kept out of the default because the baseline has no
        // string by this name, and resolving it there fails the whole capture.
        const val AUTHENTICATOR_TYPE = "type_display_authenticator"
        val ALL_TYPE_RESOURCE_NAMES = BASELINE_TYPE_RESOURCE_NAMES + AUTHENTICATOR_TYPE

        // The bottom navigation is made of text nodes too, and sits below the list rather than
        // inside it, so it is excluded before any row pairing is attempted.
        private val TAB_RESOURCE_NAMES = listOf(RECORDS_TAB, BACKUP_TAB, SETTINGS_TAB)

        // Generous: the list is eleven rows today and the loop stops as soon as the container says
        // it cannot scroll further, so this only bounds a pathological case.
        private const val MAX_SCROLLS = 20

        /**
         * One `field.<title>.<resource>=<value>` line, with the value escaped onto one line.
         *
         * Notes can span lines, and a raw newline would split one field across two oracle lines,
         * which [recordLines] would then misread. Backslashes are escaped first so the encoding
         * stays reversible. No value in Phase A's seed contains either character, so its oracle
         * bytes are unaffected.
         *
         * @param title the record's title
         * @param resourceName the field's label resource
         * @param value the value as displayed
         * @return the oracle line, without a trailing newline
         */
        fun fieldLine(title: String, resourceName: String, value: String): String {
            val escaped = value.replace("\\", "\\\\").replace("\n", "\\n")
            return "$FIELD_PREFIX$title.$resourceName=$escaped"
        }

        /**
         * The [recordLines] a vault holding exactly [records] must produce.
         *
         * For a vault defined entirely in code rather than captured from an earlier phase, like
         * the v1 fixture. Built in the order [describe] writes: the total, types sorted by
         * resource name, titles sorted by title, then every field in declaration order.
         *
         * @param records every record the vault must hold, each with its displayed values
         * @return the record lines [describe] must produce for that vault
         */
        fun expectedRecordLines(records: List<SeedRecord>): List<String> =
            listOf("$RECORD_COUNT_KEY=${records.size}") +
                records.groupingBy { it.typeResourceName }.eachCount().toSortedMap()
                    .map { (type, count) -> "$RECORD_TYPE_PREFIX$type=$count" } +
                records.sortedBy { it.title }
                    .map { "$RECORD_TITLE_PREFIX${it.title}=${it.typeResourceName}" } +
                records.flatMap { record ->
                    record.fields.map { fieldLine(record.title, it.resourceName, it.value) }
                }

        /**
         * The lines of an oracle that describe records: counts, titles and fields.
         *
         * What a comparison across a `pm clear` can hold a vault to. The backup location and the
         * settings are reset by the clear by design, and the hint belongs to whoever signed up
         * last, so none of them is a property of the records a backup carries.
         *
         * @param oracle a complete oracle, as written by any phase
         * @return its record lines, in their original order
         */
        fun recordLines(oracle: String): List<String> = oracle.lines().filter {
            it.startsWith(RECORD_COUNT_KEY) || it.startsWith(RECORD_TYPE_PREFIX) ||
                it.startsWith(RECORD_TITLE_PREFIX) || it.startsWith(FIELD_PREFIX)
        }

        /**
         * What [recordLines] must become once one record has been added to the vault (step 12).
         *
         * Derived from the baseline rather than written down, so the only difference it permits is
         * the one record: the total and that type's count go up by one, and its title line
         * appears. Every other line, including every field, must be untouched. The result is in
         * exactly the order [describe] writes: counts, then types sorted by resource name, then
         * titles sorted by title, then fields as before. Sorting by the key rather than by the
         * whole line matters, because `=` sorts after a space and would put `card 2` above `card`.
         *
         * @param oracle the oracle captured before the record was added
         * @param title the added record's title
         * @param typeResourceName the added record's type
         * @return the record lines the vault must now describe
         */
        fun recordLinesAfterAdding(
            oracle: String,
            title: String,
            typeResourceName: String,
        ): List<String> {
            val lines = recordLines(oracle)
            val count = lines.single { it.startsWith("$RECORD_COUNT_KEY=") }
                .substringAfter('=').toInt()
            val typeCounts = lines.filter { it.startsWith(RECORD_TYPE_PREFIX) }
                .associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
                .toMutableMap()
            val typeKey = "$RECORD_TYPE_PREFIX$typeResourceName"
            typeCounts[typeKey] = (typeCounts[typeKey] ?: 0) + 1
            val titles = lines.filter { it.startsWith(RECORD_TITLE_PREFIX) } +
                "$RECORD_TITLE_PREFIX$title=$typeResourceName"
            return listOf("$RECORD_COUNT_KEY=${count + 1}") +
                typeCounts.toSortedMap().map { (key, value) -> "$key=$value" } +
                titles.sortedBy { it.substringBefore('=') } +
                lines.filter { it.startsWith(FIELD_PREFIX) }
        }
    }
}
