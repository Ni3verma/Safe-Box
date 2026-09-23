package com.andryoga.safebox.upgradetest

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import java.io.File

/**
 * Reads the seeded vault back through the UI and writes down what it found.
 *
 * "The upgrade preserved the data" is not directly observable through a black box, so the harness
 * settles for something that is: a text file describing everything Phase A put there, captured the
 * same way a user would see it. Phase A's own acceptance is that ten consecutive runs produce the
 * same file byte for byte; later stages capture it again after the upgrade and diff the two.
 *
 * Three properties matter more than completeness, and shape everything below.
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
 * @param expectedTitles the titles Phase A seeded, used to reject a records list that is not the
 * one this file claims to describe
 */
internal class VaultOracle(
    private val ui: UiSupport,
    private val expectedTitles: Set<String>,
) {

    /**
     * Walks the whole app and writes the oracle to the test app's own storage.
     *
     * Written by the instrumentation rather than printed, because the host needs the exact bytes:
     * logcat adds timestamps and truncates long lines, and the acceptance is a byte comparison.
     * The test app is debuggable, so `adb run-as` can read the file back out without granting
     * anything on the device.
     *
     * @return the file written, so failures can name it
     */
    fun capture(): File {
        val text = describeVault()
        val file = File(
            InstrumentationRegistry.getInstrumentation().context.filesDir,
            ORACLE_FILE_NAME,
        )
        file.writeText(text)
        Log.i(LOG_TAG, "wrote ${text.length} bytes of oracle to $file")
        return file
    }

    private fun describeVault(): String = buildString {
        ui.clickText(RECORDS_TAB)
        val rows = collectRows()
        appendLine("$RECORD_COUNT_KEY=${rows.size}")
        rows.values.groupingBy { it }.eachCount().toSortedMap().forEach { (type, count) ->
            appendLine("record.type.$type=$count")
        }
        rows.forEach { (title, type) -> appendLine("record.title.$title=$type") }

        SeedRecord.ALL.forEach { record -> appendDetailOf(record) }
        appendBackupLocation()
        appendSettings()
    }

    /**
     * Every record in the list, as title to type.
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
     * screenful, which is where the records seeded through the UI happen to sort — Phase A wrote a
     * perfectly reproducible oracle that was missing four of eleven records before this was fixed.
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
     * @param rows what the walk found, as title to type
     */
    private fun checkRowsAreExactlyWhatWasSeeded(rows: Map<String, String>) {
        val missing = expectedTitles - rows.keys
        check(missing.isEmpty()) {
            "the records list does not hold everything Phase A seeded. Missing: $missing" +
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
     * evidence: two rows called `ui login` become one entry and a count that looks right. Here the
     * rows are still a list, so the same title on two different lines can be rejected. The list is
     * sorted by title, so a duplicate pair is adjacent and lands on one screenful.
     */
    private fun visibleRows(): Map<String, String> {
        val typeColumnLeft = ui.device.displayWidth / 2
        val screen = ui.textSnapshot().filterNot { it.text in TAB_LABELS }
        val typeNames = screen.filter { it.text in RECORD_TYPES }

        val rows = typeNames
            .filter { chip ->
                chip.bounds.left > typeColumnLeft && typeNames.count { chip.sharesLineWith(it) } == 1
            }
            .mapNotNull { chip ->
                screen.filter { it.bounds.left < typeColumnLeft && chip.sharesLineWith(it) }
                    .minByOrNull { it.bounds.top }
                    ?.let { title -> title.text to chip.text }
            }

        val duplicated = rows.groupingBy { it.first }.eachCount().filterValues { it > 1 }.keys
        check(duplicated.isEmpty()) {
            "one screenful of the records list shows $duplicated more than once, so the vault " +
                "holds a record that was created twice. That screenful read as:\n" +
                screen.joinToString("\n") { "${it.text} @ ${it.bounds.toShortString()}" }
        }

        val unexpected = rows.map { it.first }.toSet() - expectedTitles
        check(unexpected.isEmpty()) {
            "a screenful of the records list paired $unexpected with a type chip, and Phase A " +
                "never seeded a record with that title. That screenful read as:\n" +
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
     * @param record the record to read back
     */
    private fun StringBuilder.appendDetailOf(record: SeedRecord) {
        ui.scrollToTop()
        checkNotNull(ui.scrollToText(record.title)) {
            "'${record.title}' is not in the records list${ui.describeScreen()}"
        }
        ui.clickText(record.title)
        ui.awaitObject(By.desc(EDIT_RECORD_BUTTON))

        record.detailLabels.forEach { label ->
            appendLine("field.${record.title}.$label=${ui.valueBelow(label)}")
        }

        ui.device.pressBack()
        check(ui.awaitGone(By.desc(EDIT_RECORD_BUTTON))) {
            "still on the '${record.title}' detail screen after pressing back${ui.describeScreen()}"
        }
    }

    private fun StringBuilder.appendBackupLocation() {
        ui.clickText(BACKUP_TAB)
        val path = ui.awaitObject(By.textContains(GRANTED_TREE_PREFIX)).text.orEmpty()
        appendLine("backup.path=$path")
    }

    private fun StringBuilder.appendSettings() {
        ui.clickText(SETTINGS_TAB)
        SettingsChanger.OFF_BY_DEFAULT_AFTER_THIS.forEach { label ->
            val state = if (ui.retryingOnStale { ui.switchBeside(label).isChecked }) "on" else "off"
            appendLine("setting.$label=$state")
        }
    }

    companion object {
        const val ORACLE_FILE_NAME = "phase-a-oracle.txt"

        private const val LOG_TAG = "VaultOracle"

        private const val RECORD_COUNT_KEY = "record.count"
        private const val RECORDS_TAB = "Records"
        private const val BACKUP_TAB = "Backup & Restore"
        private const val SETTINGS_TAB = "Settings"
        private const val EDIT_RECORD_BUTTON = "Edit record"
        private const val GRANTED_TREE_PREFIX = "primary:"

        // The four type chips the records list renders on the right of every row.
        private val RECORD_TYPES = setOf("Login", "Card", "Bank Account", "Note")

        // The bottom navigation is made of text nodes too, and sits below the list rather than
        // inside it, so it is excluded before any row pairing is attempted.
        private val TAB_LABELS = setOf(RECORDS_TAB, BACKUP_TAB, SETTINGS_TAB)

        // Generous: the list is eleven rows today and the loop stops as soon as the container says
        // it cannot scroll further, so this only bounds a pathological case.
        private const val MAX_SCROLLS = 20
    }
}
