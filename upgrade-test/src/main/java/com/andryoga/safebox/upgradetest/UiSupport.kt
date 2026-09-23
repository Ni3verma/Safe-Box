package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
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
 */
internal class UiSupport(val device: UiDevice) {

    /** Waits for a node with exactly this visible text. */
    fun awaitText(text: String, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        awaitObject(By.text(text), timeoutMs)

    fun awaitObject(selector: BySelector, timeoutMs: Long = FIND_TIMEOUT_MS): UiObject2 =
        device.wait(Until.findObject(selector), timeoutMs)
            ?: error("could not find $selector within ${timeoutMs}ms${describeScreen()}")

    /** True if the selector is already present, without waiting for it to appear. */
    fun isPresent(selector: BySelector): Boolean = device.findObject(selector) != null

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
        device.wait(Until.findObject(By.text(text)), timeoutMs)?.let { return it }
        val scrollable = device.findObject(By.scrollable(true)) ?: return null
        repeat(maxSwipes) {
            scrollable.scroll(Direction.DOWN, SCROLL_FRACTION)
            device.waitForIdle()
            device.findObject(By.text(text))?.let { return it }
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
        private const val MAX_SWIPES = 8
        private const val SCROLL_FRACTION = 0.7f
    }
}
