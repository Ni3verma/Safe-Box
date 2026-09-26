package com.andryoga.safebox.upgradetest

import android.util.Base64
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2

/**
 * The screen checks run after every restore and after the upgrade, on the build under test.
 *
 * The backup comparison done by the host proves the *data* survived; it cannot notice a list or
 * detail screen that crashes on a record an older format left half-empty. So after each restore:
 *
 * 1. **Rows:** every (title, type) pair on the records list must equal the backup's, no more and
 *    no fewer. Rows are read through their test tags, so no screen dump and no geometry pairing.
 * 2. **Details:** one record per type is found through search and opened, which decrypts it and
 *    renders every field, then closed again. This also exercises search.
 *
 * The expected rows come from the host, which decodes the restored file with
 * `scripts/InspectBackup.java --rows`, so the harness never holds record data of its own.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 * @param expected the rows the list must show
 */
internal class RecordsCheck(
    private val ui: UiSupport,
    private val app: AppStrings,
    private val expected: Set<Row>,
) {

    /**
     * One records-list row as the app renders it.
     *
     * @property type the type chip's text, as this build renders it
     * @property title the record's title
     */
    data class Row(val type: String, val title: String)

    /** Runs both checks and leaves the unfiltered records list showing. */
    fun run() {
        ui.clickText(app.label(RECORDS_TAB))
        assertRowsMatch()
        // The walk ends at the bottom, where the collapsed app bar hides the search field.
        rewind()
        expected.groupBy { it.type }.values
            .map { rowsOfType -> rowsOfType.minBy { it.title } }
            .forEach { openAndClose(it) }
        search("")
    }

    /**
     * Walks the whole list until its rows equal [expected], or the budget runs out.
     *
     * Repeated rather than read once because the list fills asynchronously: after a restore the
     * app reports success while the list is still catching up (a one-shot read reproducibly saw
     * five of seven records).
     */
    private fun assertRowsMatch() {
        logStep("records check: expecting ${expected.size} rows")
        var seen = emptySet<Row>()
        ui.awaitCondition(
            timeoutMs = ROWS_TIMEOUT_MS,
            describe = {
                "the records list does not match the backup.\n  missing: ${expected - seen}\n" +
                    "  unexpected: ${seen - expected}"
            },
        ) {
            seen = walkRows()
            logStep("walked the list: ${seen.size} rows, ${(expected - seen).size} missing, " +
                "${(seen - expected).size} unexpected")
            seen == expected
        }
    }

    /**
     * Reads every row from the top of the list to the bottom.
     *
     * The end is detected by the visible rows not changing across a swipe, not by `scroll`'s
     * return value: that is derived from scroll accessibility events, which this list never
     * delivers (every swipe in every logcat on record logged "No scroll event received").
     *
     * @return every row seen on the way down
     */
    private fun walkRows(): Set<Row> {
        rewind()
        val seen = mutableSetOf<Row>()
        var previous = visibleRows()
        seen += previous.map { it.row }
        repeat(MAX_SWIPES) {
            swipeList(Direction.DOWN)
            val current = visibleRows()
            seen += current.map { it.row }
            if (current == previous) return seen
            previous = current
        }
        error("the records list did not end within $MAX_SWIPES swipes${ui.describeScreen()}")
    }

    /**
     * Swipes back to the top of the list. The app bar collapses while the list scrolls down and
     * returns on the way up, which is what brings the search field back.
     */
    private fun rewind() {
        var previous = visibleRows()
        repeat(MAX_SWIPES) {
            swipeList(Direction.UP)
            val current = visibleRows()
            if (current == previous) return
            previous = current
        }
        error("the records list did not reach its top within $MAX_SWIPES swipes${ui.describeScreen()}")
    }

    /**
     * Types [row]'s title into search, waits for the list to filter to it, opens it, waits for its
     * detail screen, and goes back.
     *
     * The edit button, found by content description, is the detail screen's one stable landmark.
     *
     * @param row the record to open
     */
    private fun openAndClose(row: Row) {
        logStep("open '${row.title}' (${row.type}) through search")
        search(row.title)
        ui.awaitCondition(
            timeoutMs = UiSupport.FIND_TIMEOUT_MS,
            describe = { "searching for '${row.title}' did not filter the list down to it" },
        ) {
            val rows = visibleRows().map { it.row }
            rows.contains(row) && rows.all { it.title.contains(row.title, ignoreCase = true) }
        }
        // The title, not the row: a click lands on the node's centre, which on an authenticator
        // row is the one-time-code badge, whose own copy action swallows the tap (API 35
        // emulator, 2026-09-26). The title sits inside the row's clickable area on every type.
        ui.retryingOnStale {
            rowNodes().first { readRow(it) == row }
                .findObject(By.res(RECORD_ROW_TITLE_TAG))
                .click()
        }
        ui.awaitObject(By.desc(app.label(EDIT_RECORD_BUTTON)), DETAIL_TIMEOUT_MS)
        ui.device.pressBack()
        ui.awaitObject(By.res(RECORDS_LIST_TAG))
    }

    private fun search(text: String) {
        ui.retryingOnStale { ui.awaitObject(By.clazz(UiSupport.EDIT_TEXT_CLASS)).text = text }
    }

    private fun swipeList(direction: Direction) {
        ui.retryingOnStale {
            ui.awaitObject(By.res(RECORDS_LIST_TAG)).scroll(direction, SCROLL_FRACTION)
        }
    }

    /**
     * The rows currently on screen, with where each one sits so an unmoved list can be told from a
     * moved one. A row scrolled partly out of view can lose its title or chip node; it is skipped
     * and read in full on another swipe.
     */
    private fun visibleRows(): List<VisibleRow> = ui.retryingOnStale {
        rowNodes().mapNotNull { node ->
            readRow(node)?.let { VisibleRow(it, node.visibleBounds.top) }
        }
    }

    private fun rowNodes(): List<UiObject2> =
        ui.awaitObject(By.res(RECORDS_LIST_TAG)).findObjects(By.res(RECORD_ROW_TAG))

    private fun readRow(node: UiObject2): Row? {
        val title = node.findObject(By.res(RECORD_ROW_TITLE_TAG))?.text ?: return null
        val type = node.findObject(By.res(RECORD_ROW_TYPE_TAG))?.text ?: return null
        return Row(type, title)
    }

    private data class VisibleRow(val row: Row, val top: Int)

    companion object {
        /** Instrumentation argument carrying the expected rows; see [decodeExpectedRows]. */
        const val EXPECTED_ROWS_ARGUMENT = "expectedRows"

        // Mirror TestTags in :app, which this module cannot depend on (ADR-0002).
        private const val RECORDS_LIST_TAG = "records_list"
        private const val RECORD_ROW_TAG = "record_row"
        private const val RECORD_ROW_TITLE_TAG = "record_row_title"
        private const val RECORD_ROW_TYPE_TAG = "record_row_type"

        // Resource names, resolved against the installed build. See ADR-0003.
        private const val RECORDS_TAB = "bottom_nav_records"
        private const val EDIT_RECORD_BUTTON = "cd_action_edit"

        /**
         * Backup type names, as `InspectBackup --rows` prints them, to the string resource of
         * the chip the records list shows for that type. A type missing here fails by name, which
         * is the prompt to add it when a new record type ships.
         */
        private val TYPE_CHIP_RESOURCES = mapOf(
            "LOGIN" to "type_display_login",
            "BANK_ACCOUNT" to "type_display_account",
            "BANK_CARD" to "type_display_card",
            "SECURE_NOTE" to "type_display_note",
            "AUTHENTICATOR" to "type_display_authenticator",
        )

        private const val ROWS_TIMEOUT_MS = 20_000L
        private const val DETAIL_TIMEOUT_MS = 10_000L
        private const val MAX_SWIPES = 10
        private const val SCROLL_FRACTION = 0.7f

        /**
         * Decodes the host's expected-rows argument into rows as this build renders them.
         *
         * The host passes base64 because `am instrument -e` values cannot safely carry tabs,
         * newlines or non-ASCII titles, and the app cannot read a file the host pushes to
         * `/data/local/tmp`.
         *
         * @param base64 base64 of UTF-8 `TYPE<tab>title` lines, as `InspectBackup --rows` prints
         * @param app the installed build's labels, used to render each type's chip text
         * @return the rows the records list must show
         */
        fun decodeExpectedRows(base64: String, app: AppStrings): Set<Row> {
            val text = String(Base64.decode(base64, Base64.DEFAULT), Charsets.UTF_8)
            val rows = text.lines().filter { it.isNotEmpty() }.map { line ->
                val type = line.substringBefore('\t')
                val title = line.substringAfter('\t')
                val chip = TYPE_CHIP_RESOURCES[type]
                    ?: error("no records-list chip is known for backup type '$type'; add it to TYPE_CHIP_RESOURCES")
                Row(app.label(chip), title)
            }
            check(rows.isNotEmpty()) { "the expected-rows argument decodes to no rows" }
            return rows.toSet()
        }
    }
}
