package com.andryoga.safebox.upgradetest.store

import android.os.SystemClock
import androidx.test.uiautomator.UiDevice
import com.andryoga.safebox.upgradetest.logStep

/**
 * Writes one raw screen capture per call, as `<name>-<theme>.png`, or `<name>-<theme>-<variant>.png`
 * when the host asked for a variant, in a directory the host pulls afterwards.
 *
 * `screencap` runs through the shell rather than `UiDevice.takeScreenshot` for two reasons: the
 * shell can write to `/data/local/tmp`, which the host can `adb pull` and nobody can see in the
 * app's file picker, and it photographs the real frame buffer, so a SystemUI window such as the
 * biometric sheet is in the picture. Neither route can capture a `FLAG_SECURE` window, which is
 * why the tour turns Privacy mode off first.
 *
 * A short settle precedes every capture. Animations are scaled to zero by the host, but the
 * one-time-code countdown rings and the keyboard still take a frame or two.
 *
 * @param device the device under test
 * @param directory on-device directory to write into; created if missing
 * @param theme appearance suffix for the file name, `light` or `dark`
 * @param variant extra file-name segment for captures taken under a device state only the host
 * can set, such as a Material You seed colour; null for the regular captures
 */
internal class SceneCapture(
    private val device: UiDevice,
    private val directory: String,
    private val theme: String,
    private val variant: String? = null,
) {

    init {
        device.executeShellCommand("mkdir -p $directory")
    }

    /**
     * Captures the screen as it is now.
     *
     * @param name the capture's name, which the renderer's scenes.json refers to
     */
    fun capture(name: String) {
        val suffix = variant?.let { "-$it" }.orEmpty()
        val path = "$directory/$name-$theme$suffix.png"
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MS)
        device.executeShellCommand("screencap -p $path")
        val size = device.executeShellCommand("stat -c %s $path").trim().toLongOrNull() ?: 0L
        check(size > MIN_PNG_BYTES) {
            "screencap wrote $size bytes to $path; a real capture is far larger, so the capture " +
                "failed or $directory is not writable by the shell"
        }
        logStep("captured $path ($size bytes)")
    }

    private companion object {
        const val SETTLE_MS = 750L

        // A solid-colour 1080x2400 PNG is a few kilobytes; anything smaller is an error page.
        const val MIN_PNG_BYTES = 2_000L
    }
}
