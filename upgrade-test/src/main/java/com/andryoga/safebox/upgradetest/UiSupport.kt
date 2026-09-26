package com.andryoga.safebox.upgradetest

import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.ByteArrayOutputStream

/** Logcat tag of every harness step; the runners save these lines as `harness-steps.txt`. */
private const val HARNESS_LOG_TAG = "SafeBoxHarness"

/**
 * Logs one harness step, so a failure can be read as the sequence of steps that led to it
 * rather than only the screen it ended on. Never pass record field values or passwords.
 *
 * @param message what the harness is about to do, or what it just observed
 */
internal fun logStep(message: String) {
    Log.i(HARNESS_LOG_TAG, message)
}

/**
 * Shared UI Automator plumbing for the upgrade and restore harness.
 *
 * Every lookup here waits. A one-shot `findObject` is a race against the next frame, and on a cold
 * CI emulator that race is lost often enough to look like a real defect.
 *
 * Every lookup also re-reads the screen from the app rather than from this process's accessibility
 * cache — see [flushAccessibilityCache] for what goes wrong otherwise.
 *
 * Every failure message ends with the window hierarchy ([describeScreen]), because a CI failure on
 * an emulator nobody can attach to is only triageable if it carries the screen it failed on.
 *
 * @param device the device under test
 */
internal class UiSupport(val device: UiDevice) {

    /** Waits for a node with exactly this visible text. */
    fun awaitText(text: String, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        awaitObject(By.text(text), timeoutMs)

    /** Waits for a node matching [selector], failing with the screen attached if it never shows. */
    fun awaitObject(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        findOrNull(selector, timeoutMs) ?: run {
            logStep("gave up waiting ${timeoutMs}ms for $selector")
            error("could not find $selector within ${timeoutMs}ms${describeScreen()}")
        }

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

    /** Every node matching the selector, read from a freshly emptied cache. */
    fun findAll(selector: BySelector): List<UiObject2> {
        flushAccessibilityCache()
        return device.findObjects(selector)
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

    /**
     * Polls [condition] until it returns true, failing with [describe]'s message otherwise.
     *
     * For conditions that span several nodes (a filtered list, a set of rows), which no single
     * selector can express.
     *
     * @param timeoutMs how long to keep trying
     * @param describe builds the failure message; called once, only on failure
     * @param condition re-reads whatever it checks on every call
     */
    fun awaitCondition(timeoutMs: Long, describe: () -> String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            flushAccessibilityCache()
            if (retryingOnStale { condition() }) return
            if (SystemClock.uptimeMillis() >= deadline) {
                val message = describe()
                logStep("gave up after ${timeoutMs}ms: $message")
                error(message + describeScreen())
            }
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
     * @param selector what to tap
     * @param timeoutMs budget covering both finding it and getting a tap to land
     */
    fun clickObject(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS) {
        logStep("tap $selector")
        retryingOnStale(timeoutMs) { awaitObject(selector, timeoutMs).click() }
    }

    /**
     * Types into the field belonging to a label, re-finding it if it goes stale, and does not
     * return until the app has taken the value.
     *
     * The read-back is the important part. `ACTION_SET_TEXT` returns as soon as the *node* accepts
     * the text; the app's state is updated later, when Compose routes it through `onValueChange`.
     * A tap on the form's button right after can race that hop and submit an **empty** value
     * rather than fail: CI run 35875270225 saved a record with no title that way, 168 ms after
     * setting it, after ten passing local runs on a faster device.
     *
     * Emptiness is the condition, not equality: masked fields report their masked rendering
     * rather than the text that was typed.
     *
     * @param label the field's visible label
     * @param value the text to set
     */
    fun typeInto(label: String, value: String) {
        logStep("type ${value.length} chars into '$label'")
        retryingOnStale { textField(label).text = value }
        if (value.isEmpty()) return
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (true) {
            flushAccessibilityCache()
            val shown = retryingOnStale { textField(label).text }
            if (!shown.isNullOrEmpty()) return
            check(SystemClock.uptimeMillis() < deadline) {
                "the '$label' field is still empty after being set to '$value', so the app never " +
                    "took the value${describeScreen()}"
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    /**
     * Replaces the text in a masked field that already holds something, and does not return until
     * the app has taken the new value.
     *
     * [typeInto]'s "no longer empty" read-back is useless once a field already holds text. A
     * masked field renders one bullet per character, so its *length* is observable even though its
     * content is not, and that is what is waited on. The caller therefore has to make sure the old
     * and new values differ in length.
     *
     * @param label the field's visible label
     * @param value the text to set; must differ in length from what the field holds
     */
    fun retypeMasked(label: String, value: String) {
        logStep("retype ${value.length} chars into masked '$label'")
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
     * Resolves the editable field belonging to a Compose `OutlinedTextField`.
     *
     * The label and the editable node are separate leaves, so the label is located first and its
     * container searched for the `EditText` that Compose exposes to the accessibility layer. This
     * is why text fields need no test tag.
     */
    private fun textField(label: String): UiObject2 {
        val labelNode = awaitText(label)
        return labelNode.parent?.findObject(By.clazz(EDIT_TEXT_CLASS))
            ?: error("no editable field beside the '$label' label${describeScreen()}")
    }

    /**
     * Resolves the switch belonging to a settings row, by geometry.
     *
     * N-1 releases have no settings tags, and their settings screen is a flat column: label and
     * switch are siblings with no row container between them, and the switch carries neither text
     * nor content description. What does hold is that a switch sits on the same line as its
     * label. Kept for N as well until N-1 carries the tags, so one step has one lookup (see the
     * follow-ups in docs/testing/upgrade-testing.md).
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

    /**
     * Finds a node on a short scrolling screen, swiping down until it shows.
     *
     * For screens of a few cards (the Backup & Restore tab, the document picker's root), not for
     * the records list, which [RecordsCheck] walks by its own rows. The swipe goes to the largest
     * scrollable node, because a screen can nest a small horizontal scroller inside the big one.
     * `scroll`'s return value is not trusted to mean "moved"; the swipe budget ends the search.
     *
     * @param selector what to find
     * @param maxSwipes how many swipes to spend before failing
     * @return the node
     */
    fun scrollTo(selector: BySelector, maxSwipes: Int = SHORT_SCREEN_SWIPES): UiObject2 {
        findOrNull(selector, QUICK_LOOK_MS)?.let { return it }
        repeat(maxSwipes) { swipe ->
            retryingOnStale {
                findAll(By.scrollable(true))
                    .maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
                    ?.scroll(Direction.DOWN, SCROLL_FRACTION)
            }
            findOrNull(selector, 0)?.let {
                logStep("scrolled to $selector after ${swipe + 1} swipe(s)")
                return it
            }
        }
        logStep("gave up scrolling to $selector after $maxSwipes swipes")
        error("could not find $selector within $maxSwipes swipes${describeScreen()}")
    }

    /**
     * The window hierarchy, appended to every failure message.
     *
     * @return the dump, prefixed with a newline so it reads as a block after the message
     */
    fun describeScreen(): String = buildString {
        append("\ncurrent window hierarchy:\n")
        append(
            runCatching {
                val sink = ByteArrayOutputStream()
                device.dumpWindowHierarchy(sink)
                sink.toString(Charsets.UTF_8.name())
            }.getOrElse { "  <unavailable: $it>" },
        )
    }

    /**
     * Discards the accessibility node cache this process holds for the app under test.
     *
     * Without this the harness reads a tree that is a navigation behind reality. Observed on
     * 2026-09-23 on a Pixel 8 API 35 emulator: after returning from a form, the records list was
     * reported underneath *the form's* app bar for a full 60 s wait, and correctly the moment the
     * instrumentation process (and its cache) died.
     *
     * `setCompressedLayoutHeirarchy` is used for its side effect rather than its meaning: it calls
     * through to `UiAutomation.setServiceInfo`, which empties the cache. The value passed is the
     * UiAutomator default, so the hierarchy that comes back is unchanged.
     */
    private fun flushAccessibilityCache() {
        device.setCompressedLayoutHeirarchy(false)
    }

    companion object {
        const val FIND_TIMEOUT_MS = 15_000L
        const val EDIT_TEXT_CLASS = "android.widget.EditText"

        private const val POLL_INTERVAL_MS = 250L

        // What scrollTo spends before it starts swiping: a few polls of a screen that already
        // shows the node, short enough that looking below the fold stays cheap.
        private const val QUICK_LOOK_MS = 2_000L
        private const val SHORT_SCREEN_SWIPES = 4
        private const val SCROLL_FRACTION = 0.7f
    }
}
