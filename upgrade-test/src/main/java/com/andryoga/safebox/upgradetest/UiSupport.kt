package com.andryoga.safebox.upgradetest

import android.graphics.Rect
import android.os.SystemClock
import android.util.Xml
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.StringReader

/**
 * Shared UI Automator plumbing for the upgrade harness.
 *
 * Extracted from the phase tests because a second, unrelated caller now needs the same behaviour:
 * [SafDocumentPicker] drives DocumentsUI rather than the app, and duplicating the
 * failure-description logic would have meant a picker failure that arrives without the screen it
 * failed on — which is the one thing that makes a CI failure triageable at all.
 *
 * Every lookup here waits. A one-shot `findObject` is a race against the next frame, and on a cold
 * CI emulator that race is lost often enough to look like a real defect.
 *
 * Every lookup also re-reads the screen from the app rather than from this process's accessibility
 * cache — see [flushAccessibilityCache] for what goes wrong otherwise.
 */
internal class UiSupport(val device: UiDevice) {

    /** Waits for a node with exactly this visible text. */
    fun awaitText(text: String, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        awaitObject(By.text(text), timeoutMs)

    fun awaitObject(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        findOrNull(selector, timeoutMs)
            ?: error("could not find $selector within ${timeoutMs}ms${describeScreen()}")

    /** True if the selector is already present, without waiting for it to appear. */
    fun isPresent(selector: BySelector): Boolean = findOrNull(selector, 0) != null

    /**
     * Polls for a node until it appears or the budget runs out.
     *
     * `UiDevice.wait` is deliberately not used: it would take one snapshot of the tree and keep
     * matching against it, which is precisely the failure mode [flushAccessibilityCache] exists to
     * avoid. Polling by hand costs one binder call per interval and makes every attempt see the
     * screen as it is now.
     *
     * @param selector what to look for
     * @param timeoutMs how long to keep trying; 0 means a single attempt
     * @return the node, or null if the budget ran out
     */
    fun findOrNull(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2? {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            flushAccessibilityCache()
            device.findObject(selector)?.let { return it }
            if (SystemClock.uptimeMillis() >= deadline) return null
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    /**
     * Waits for a node to disappear.
     *
     * @param selector what should go away
     * @param timeoutMs how long to allow
     * @return true if it is gone, false if it was still there when the budget ran out
     */
    fun awaitGone(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            flushAccessibilityCache()
            if (device.findObject(selector) == null) return true
            if (SystemClock.uptimeMillis() >= deadline) return false
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    /** Waits for a node with exactly this visible text, then taps it. */
    fun clickText(text: String, timeoutMs: Long = FIND_TIMEOUT_MS) =
        clickObject(By.text(text), timeoutMs)

    /**
     * Waits for a node, then taps it, re-finding it if it goes stale in between.
     *
     * Finding and tapping are two separate round trips to the app, and anything that re-lays out
     * between them — a drawer still sliding open, a list settling — invalidates the handle. UI
     * Automator reports that as [StaleObjectException] out of `click()`, which reads as a harness
     * crash rather than as the retryable timing problem it is. Observed on 2026-09-23 tapping
     * `Downloads` in the document picker's roots drawer, one tap after opening it.
     *
     * Re-finding is correct rather than merely convenient here: the node is identified by a
     * selector, so a fresh lookup asks for the same thing the caller asked for.
     *
     * @param selector what to tap
     * @param timeoutMs budget covering both finding it and getting a tap to land
     */
    fun clickObject(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS) {
        retryingOnStale(timeoutMs) { awaitObject(selector, timeoutMs).click() }
    }

    /**
     * Types into the field belonging to a label, re-finding it if it goes stale, and does not
     * return until the app has taken the value.
     *
     * Same hazard as [clickObject]: setting text is a second round trip, and forms that are still
     * animating in invalidate the handle that was just found.
     *
     * The read-back is the important part, and it is not defensive programming. `ACTION_SET_TEXT`
     * returns as soon as the *node* accepts the text; the app's state is updated later, when
     * Compose routes it through `onValueChange`. The forms save whatever the ViewModel holds at
     * the moment Save is pressed, so a harness that types and immediately taps Save is racing that
     * hop — and losing the race saves an **empty** record rather than failing. CI run
     * 35875270225 lost it: the Note form's two fields were set 168 ms before Save, the record was
     * written with no title, and the run died much later looking for a row that was never going to
     * exist. The same code had passed ten consecutive local runs, because a faster device wins the
     * race every time.
     *
     * Emptiness is the condition, not equality: sensitive fields report their masked rendering
     * rather than the text that was typed, so requiring the value back would fail on exactly the
     * fields whose contents matter most.
     *
     * @param label the field's visible label
     * @param value the text to set
     */
    fun typeInto(label: String, value: String) {
        retryingOnStale { textField(label).text = value }
        if (value.isEmpty()) return
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (true) {
            flushAccessibilityCache()
            val shown = retryingOnStale { textField(label).text }
            if (!shown.isNullOrEmpty()) return
            check(SystemClock.uptimeMillis() < deadline) {
                "the '$label' field is still empty after being set to '$value', so the app never " +
                    "took the value and saving now would write an empty record${describeScreen()}"
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    /**
     * Replaces the text in a masked field that already holds something, and does not return until
     * the app has taken the new value.
     *
     * [typeInto]'s "no longer empty" read-back is useless once a field already holds text: it is
     * true before the new value has reached the app. A masked field renders one bullet per
     * character, so its *length* is observable even though its content is not, and that is what is
     * waited on here. The caller therefore has to make sure the old and new values differ in
     * length. For the only caller, a wrong password followed by the right one, that is a choice of
     * test data.
     *
     * @param label the field's visible label
     * @param value the text to set; must differ in length from what the field holds
     */
    fun retypeMasked(label: String, value: String) {
        retryingOnStale { textField(label).text = value }
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (true) {
            flushAccessibilityCache()
            val shownLength = retryingOnStale { textField(label).text }?.length ?: 0
            if (shownLength == value.length) return
            check(SystemClock.uptimeMillis() < deadline) {
                "the '$label' field shows $shownLength characters after being set to a " +
                    "${value.length}-character value, so the app never took it${describeScreen()}"
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    /**
     * Runs an action until it completes without hitting a stale node handle.
     *
     * Exposed rather than kept private because not every interaction is a tap: reading a switch's
     * state and then toggling it is two round trips against one handle, and either can be the one
     * that goes stale.
     *
     * @param timeoutMs how long to keep retrying before letting the exception out
     * @param action what to attempt; it must re-find whatever it touches
     * @return whatever the action returned
     */
    fun <T> retryingOnStale(timeoutMs: Long = FIND_TIMEOUT_MS, action: () -> T): T {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            try {
                return action()
            } catch (stale: StaleObjectException) {
                if (SystemClock.uptimeMillis() >= deadline) throw stale
                SystemClock.sleep(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Discards the accessibility node cache this process holds for the app under test.
     *
     * Without this the harness reads a tree that is a navigation behind reality. Observed on
     * 2026-09-23 driving `v2.0.4.0` on a Pixel 8 API 35 emulator: after returning from the
     * add-record form, the records list was reported underneath *the form's* app bar, so the add
     * button did not exist as far as the test was concerned — for a full 60s wait, and then
     * correctly the moment the instrumentation process (and its cache) died. The app itself was
     * fine throughout; only this process's view of it was stale.
     *
     * `setCompressedLayoutHeirarchy` is used for its side effect rather than its meaning: it calls
     * through to `UiAutomation.setServiceInfo`, which empties the cache. The value passed is the
     * UiAutomator default, so the hierarchy that comes back is unchanged.
     */
    private fun flushAccessibilityCache() {
        device.setCompressedLayoutHeirarchy(false)
    }

    /**
     * Resolves the editable field belonging to a Compose `OutlinedTextField`.
     *
     * The label and the editable node are separate leaves, so the label is located first and its
     * container searched for the `EditText` that Compose exposes to the accessibility layer.
     */
    fun textField(label: String): UiObject2 {
        val labelNode = awaitText(label)
        return labelNode.parent?.findObject(By.clazz("android.widget.EditText"))
            ?: error("no editable field beside the '$label' label${describeScreen()}")
    }

    /**
     * Resolves the switch belonging to a settings row.
     *
     * Geometry is used because nothing else connects the two. The baseline's settings screen is a
     * flat column: the label and the switch are siblings at the same depth with no row container
     * between them, and the switch carries neither text nor content description. The one thing that
     * does hold is that a switch sits on the same line as its label, so the switch whose vertical
     * extent overlaps the label's is the right one.
     *
     * @param label the row's visible title
     * @return the checkable node on that row
     */
    fun switchBeside(label: String): UiObject2 {
        val labelBounds = awaitText(label).visibleBounds
        val switches = findAll(By.checkable(true))
        return switches.firstOrNull {
            it.visibleBounds.top < labelBounds.bottom && it.visibleBounds.bottom > labelBounds.top
        } ?: error(
            "no switch on the same line as '$label' among ${switches.size} switches on " +
                "screen${describeScreen()}",
        )
    }

    /** Every node matching the selector, read from a freshly emptied cache. */
    fun findAll(selector: BySelector): List<UiObject2> {
        flushAccessibilityCache()
        return device.findObjects(selector)
    }

    /**
     * Every piece of text on screen, with where it was, read as one consistent snapshot.
     *
     * Not `findObjects` + `getText()`: those are separate binder calls per node, and a `UiObject2`
     * re-resolves its underlying node each time it is touched, so while a list is scrolling the
     * text and the position a caller reads back can come from two different frames. Anything that
     * pairs nodes by where they are - which is the only way to read this app's rows and detail
     * screens - is wrong the moment that happens, and wrong in a way that looks like a layout fact
     * rather than a race.
     *
     * Dumping the window hierarchy is one call that returns one frame, so text and position cannot
     * disagree. It also un-escapes XML entities, which matters for the one label in this app
     * containing an ampersand.
     *
     * @param packageName when set, only nodes owned by this package are returned. Diffing two
     * snapshots needs it: the hierarchy includes SystemUI, and its status-bar clock changes text
     * whenever the minute turns - which once made the clock look like the revealed password hint
     * @return the visible text nodes; empty text is dropped as it carries no information
     */
    fun textSnapshot(packageName: String? = null): List<ScreenText> {
        flushAccessibilityCache()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(device.windowHierarchy()))
        val texts = mutableListOf<ScreenText>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != NODE_TAG) continue
            val text = parser.getAttributeValue(null, TEXT_ATTRIBUTE).orEmpty()
            if (text.isEmpty()) continue
            if (packageName != null &&
                parser.getAttributeValue(null, PACKAGE_ATTRIBUTE) != packageName
            ) {
                continue
            }
            parseBounds(parser.getAttributeValue(null, BOUNDS_ATTRIBUTE))
                ?.let { texts += ScreenText(text, it) }
        }
        return texts
    }

    /**
     * Reads the value a read-only detail screen renders under a label.
     *
     * The detail screens are label/value pairs with no container tying the two together and no
     * content description on either, so the value is the nearest text *below* the label whose
     * horizontal extent overlaps it. Two-column rows make the horizontal test load-bearing: `MICR
     * Code` and `IFSC Code` sit on the same line, and "nearest below" alone would read the wrong
     * one.
     *
     * Do not tighten this to "strictly below the label's bottom edge", however tempting it reads:
     * measured on 2026-09-23, a field's value node *vertically overlaps* its own label's node on
     * these screens, so excluding same-line text drops the value and the nearest-below race is
     * then won by the *next* label. That fails silently - every field still yields a plausible
     * string - and it moved 11 of the 23 captured fields, turning `User Id` into `Password` and
     * `CVV` into the `Created on` timestamp. The oracle hash is what caught it.
     *
     * The label is scrolled to first, because the bank account screen is taller than a phone.
     *
     * @param label the field's visible label, as the detail screen spells it
     * @return the rendered value, which is the *displayed* string - card numbers arrive grouped
     * into fours and expiry dates carry the slash the input field refuses
     */
    fun valueBelow(label: String): String {
        checkNotNull(scrollToText(label)) {
            "the detail screen has no '$label' label${describeScreen()}"
        }
        val screen = textSnapshot()
        val labelBounds = screen.firstOrNull { it.text == label }?.bounds
            ?: error("'$label' disappeared between being scrolled to and being read")
        return screen
            .filter {
                it.bounds.top > labelBounds.top &&
                    it.bounds.left < labelBounds.right &&
                    it.bounds.right > labelBounds.left
            }
            .minByOrNull { it.bounds.top }
            ?.text
            ?: error("nothing is rendered under the '$label' label${describeScreen()}")
    }

    /**
     * Where a piece of text was when the screen was read.
     *
     * @param text the visible text
     * @param bounds its position, in device pixels
     */
    data class ScreenText(val text: String, val bounds: Rect) {

        /**
         * Whether this text and [other] are rendered on the same line.
         *
         * Vertical overlap rather than equal tops, because text on one line of a list row is not
         * the same height: a title, its type chip and a filter chip all sit on lines of their own
         * but none of them start at the same pixel. Note that a text always shares a line with
         * itself, which is what lets callers count how many things are on a line.
         *
         * @param other the text to compare against
         * @return true when the two vertical extents overlap at all
         */
        fun sharesLineWith(other: ScreenText): Boolean =
            bounds.top < other.bounds.bottom && bounds.bottom > other.bounds.top
    }

    /**
     * Turns the hierarchy dump's `[left,top][right,bottom]` into a rectangle.
     *
     * @param raw the attribute value, or null when the node had none
     * @return the rectangle, or null if the attribute was missing or malformed
     */
    private fun parseBounds(raw: String?): Rect? {
        val match = raw?.let { BOUNDS_PATTERN.matchEntire(it) } ?: return null
        val (left, top, right, bottom) = match.destructured
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    /**
     * Scrolls the screen's scrollable container one step.
     *
     * A screen with nothing to scroll is not an error - a list short enough to fit exposes no
     * scrollable node at all - so that case reports "no further" rather than failing.
     *
     * "The" container is the scrollable node with the largest visible area, not the first one
     * found. A screen can nest a small scrollable inside the big one: from the release that added
     * the Authenticator type, the records list's filter chips no longer fit on one line and become
     * a `HorizontalScrollView` inside the list. `By.scrollable(true)` returned that row, a vertical
     * scroll of a horizontal row moves nothing and reports "no further", and the walk silently
     * stopped after the first screenful - seen on 2026-09-24 as four records "missing" from an
     * upgraded vault that held all of them.
     *
     * Whether the swipe moved anything is decided by comparing what the app shows before and after
     * it, **not** by `UiObject2.scroll`'s return value. That value is derived from
     * `TYPE_VIEW_SCROLLED` accessibility events, and this app's records list never delivers one to
     * the harness: every swipe in every logcat on record logs "No scroll event received after
     * scroll" and returns false (18 of 18 in CI run 35970915467, 22 of 22 in the passing MR3 run
     * 35957354466, 28 of 28 in a local emulator-5554 run). Trusting it meant every walk stopped
     * after one swipe and every rewind after one swipe up. Locally one swipe happened to reach the
     * last record; on CI it fell two rows short, and `note` was reported missing from a vault that
     * held it (2026-09-24). The comparison is limited to the app's own nodes for the same reason
     * [textSnapshot] offers the filter: the status-bar clock would otherwise make an unmoved list
     * look moved whenever the minute turns.
     *
     * The cost is one extra swipe at each end of a walk, to observe that nothing changed.
     *
     * "Changed" means that text present in *both* snapshots moved, not that the snapshots differ.
     * From the release that added authenticators, a row can hold text that changes on its own: the
     * countdown ring's seconds label every second and the code every 30 s. With a plain comparison
     * an unmoved list showing such a row looks moved on every swipe, so every walk runs to its
     * swipe limit. Text that appears or disappears is ignored, and a real scroll still keeps part
     * of the previous screenful on screen, whose titles are unique and therefore change position.
     * If no text survives the swipe at all, that counts as moved.
     *
     * @param direction which way to go
     * @param fraction how much of the container to travel
     * @return true if the swipe moved what the app shows, so callers can walk a list to its end
     * without guessing how long it is; false at the end, or when there is nothing to scroll
     */
    fun scrollList(direction: Direction, fraction: Float = SCROLL_FRACTION): Boolean {
        val appPackage = device.currentPackageName
        val before = textSnapshot(appPackage).topsByText()
        retryingOnStale {
            flushAccessibilityCache()
            val container = device.findObjects(By.scrollable(true))
                .maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
                ?: return@retryingOnStale null
            container.scroll(direction, fraction)
        } ?: return false
        val after = textSnapshot(appPackage).topsByText()
        val shared = before.keys intersect after.keys
        if (shared.isEmpty()) return before != after
        return shared.any { before[it] != after[it] }
    }

    /**
     * Where each distinct text sits vertically, for [scrollList]'s movement test.
     *
     * A list of tops rather than one, because the same text can appear on several rows — every
     * login row carries a "Login" chip.
     */
    private fun List<ScreenText>.topsByText(): Map<String, List<Int>> =
        groupBy({ it.text }, { it.bounds.top }).mapValues { (_, tops) -> tops.sorted() }

    /**
     * Rewinds a scrollable screen to the top.
     *
     * Needed more often than it looks. The records list is under a *collapsing* app bar, so the
     * add-record button does not exist in the hierarchy at all while the list is scrolled down —
     * and every `scrollToText` leaves it scrolled down. Searching for that button without
     * rewinding first waits out its whole timeout against a screen that is behaving perfectly.
     * `scrollToText` itself only travels downwards, so this is also how a lookup is made
     * independent of where the previous one finished.
     *
     * @param maxSwipes how many screens to travel before giving up
     */
    fun scrollToTop(maxSwipes: Int = MAX_SWIPES) {
        repeat(maxSwipes) {
            if (!scrollList(Direction.UP)) return
        }
    }

    /**
     * Finds text that may not be on screen yet, or may be below the fold.
     *
     * Two passes, because the two reasons text is not visible want opposite treatment.
     *
     * The first pass is impatient: glance at the screen, then scroll, looking after each swipe.
     * Text that is merely below the fold is found in the time it takes to get there.
     *
     * The second pass is patient, and only happens when the first found nothing. It rewinds and
     * repeats the walk, this time waiting [timeoutMs] for the text to appear before scrolling.
     * That is the case the wait was added for: list content arrives asynchronously, and after a
     * restore the app shows "Data has been successfully restored." while the records list is still
     * catching up — a one-shot query reproducibly saw five of seven records. The app's success
     * message is not a completion signal for the data being queryable.
     *
     * Spending the patience *before* the scrolling, which is what this used to do, charges the full
     * timeout to every lookup whose only crime is sorting below the fold. Measured from
     * UiAutomator's own poll logging: twelve such lookups, 181 s of a 269 s Phase A, every one of
     * them for a record that was on screen one swipe later.
     *
     * The scrolling goes through [scrollList] rather than holding one container handle for the
     * whole walk. This function used to keep a handle across up to eight swipes, which is the
     * pattern the rest of the harness exists to avoid: the handle re-resolves on every use, and a
     * list that re-lays out mid-walk invalidates it. It also bypassed the accessibility-cache
     * flush that every other lookup performs.
     *
     * @param text the exact visible text to look for
     * @param timeoutMs how long the patient pass waits for the text to appear
     * @param maxSwipes how many screens to travel before giving up
     * @return the node, or null if it never appeared
     */
    fun scrollToText(
        text: String,
        timeoutMs: Long = FIND_TIMEOUT_MS,
        maxSwipes: Int = MAX_SWIPES,
    ): UiObject2? {
        walkForText(text, QUICK_LOOK_MS, maxSwipes)?.let { return it }
        scrollToTop(maxSwipes)
        return walkForText(text, timeoutMs, maxSwipes)
    }

    /**
     * One downward walk looking for [text], from wherever the screen currently is.
     *
     * The text is looked for *after* the swipe that reports there is nothing left to scroll,
     * because that swipe still reveals a screenful. Stopping on the report rather than after it is
     * how the oracle came to miss the last four records in the list.
     *
     * @param text the exact visible text to look for
     * @param initialTimeoutMs how long to wait before starting to scroll
     * @param maxSwipes how many screens to travel before giving up
     * @return the node, or null if it was not found in this walk
     */
    private fun walkForText(text: String, initialTimeoutMs: Long, maxSwipes: Int): UiObject2? {
        findOrNull(By.text(text), initialTimeoutMs)?.let { return it }
        repeat(maxSwipes) {
            val more = scrollList(Direction.DOWN)
            findOrNull(By.text(text), 0)?.let { return it }
            if (!more) return null
        }
        return null
    }

    /**
     * A CI failure on an emulator nobody can attach to is only actionable if it carries the screen
     * with it, so every failure message in the harness ends with the window hierarchy.
     */
    fun describeScreen(): String = buildString {
        append("\ncurrent window hierarchy:\n")
        append(runCatching { device.windowHierarchy() }.getOrElse { "  <unavailable: $it>" })
    }

    private fun UiDevice.windowHierarchy(): String {
        val sink = ByteArrayOutputStream()
        dumpWindowHierarchy(sink)
        return sink.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val FIND_TIMEOUT_MS = 15_000L

        // Shape of one element of UiDevice.dumpWindowHierarchy's output.
        private const val NODE_TAG = "node"
        private const val TEXT_ATTRIBUTE = "text"
        private const val BOUNDS_ATTRIBUTE = "bounds"
        private const val PACKAGE_ATTRIBUTE = "package"
        private val BOUNDS_PATTERN = Regex("""\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")

        private const val POLL_INTERVAL_MS = 250L

        // What a lookup spends before it starts scrolling. Long enough for a few polls of a screen
        // that is already showing the text, short enough that looking in the wrong place is cheap.
        private const val QUICK_LOOK_MS = 2_000L
        private const val MAX_SWIPES = 8
        private const val SCROLL_FRACTION = 0.7f
    }
}
