package com.andryoga.safebox.test.fakes

import android.content.ClipboardManager
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/**
 * In-memory [Clipboard] for Compose UI tests.
 *
 * Reading the real system clipboard from a test is restricted to the focused app from Android 10
 * onwards and leaks state between test methods. Substituting this through `LocalClipboard` keeps
 * the assertion on exactly what the composable asked to copy.
 */
class FakeClipboard : Clipboard {

    /** Last entry written by the code under test, or null when nothing was copied. */
    var lastClipEntry: ClipEntry? = null
        private set

    /** Plain text of [lastClipEntry], which is what every copy affordance in the app writes. */
    val lastCopiedText: String?
        get() = lastClipEntry?.clipData?.getItemAt(0)?.text?.toString()

    @Deprecated("mirrors the deprecation on Clipboard; Safe-Box never touches the platform handle")
    override val nativeClipboard: ClipboardManager
        get() = throw UnsupportedOperationException(
            "the platform clipboard is deliberately unreachable from tests",
        )

    override suspend fun getClipEntry(): ClipEntry? = lastClipEntry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        lastClipEntry = clipEntry
    }
}
