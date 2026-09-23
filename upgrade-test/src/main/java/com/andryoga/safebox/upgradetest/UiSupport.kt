package com.andryoga.safebox.upgradetest

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.ByteArrayOutputStream

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
     * Types into the field belonging to a label, re-finding it if it goes stale.
     *
     * Same hazard as [clickObject]: setting text is a second round trip, and forms that are still
     * animating in invalidate the handle that was just found.
     *
     * @param label the field's visible label
     * @param value the text to set
     */
    fun typeInto(label: String, value: String) {
        retryingOnStale { textField(label).text = value }
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
     * Finds text that may not be on screen yet, or may be below the fold.
     *
     * Both halves are needed, for different reasons.
     *
     * It waits first because list content arrives asynchronously: after a restore, the app shows
     * "Data has been successfully restored." while the records list is still catching up, and a
     * one-shot query reproducibly saw five of seven records. The app's success message is not a
     * completion signal for the data being queryable, so the harness must not treat it as one.
     *
     * It scrolls second because the list is only as long as the fixture makes it, and the CI
     * emulator is not the same size as the developer one — "it was visible when I wrote the test"
     * is not a property that survives. Note that a list short enough to fit reports no scrollable
     * container at all, which is why a missing container is not itself an error.
     *
     * @param text the exact visible text to look for
     * @param timeoutMs how long to wait for it to appear before scrolling for it
     * @param maxSwipes how many screens to travel before giving up
     * @return the node, or null if it never appeared
     */
    fun scrollToText(
        text: String,
        timeoutMs: Long = FIND_TIMEOUT_MS,
        maxSwipes: Int = MAX_SWIPES,
    ): UiObject2? {
        findOrNull(By.text(text), timeoutMs)?.let { return it }
        val scrollable = device.findObject(By.scrollable(true)) ?: return null
        repeat(maxSwipes) {
            scrollable.scroll(Direction.DOWN, SCROLL_FRACTION)
            device.waitForIdle()
            findOrNull(By.text(text), 0)?.let { return it }
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
        private const val POLL_INTERVAL_MS = 250L
        private const val MAX_SWIPES = 8
        private const val SCROLL_FRACTION = 0.7f
    }
}
