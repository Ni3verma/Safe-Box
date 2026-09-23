package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * Drives the system document picker (DocumentsUI) to select a file the host pushed to Downloads.
 *
 * This is the flakiest interaction in the whole harness, so it lives behind one helper rather than
 * being open-coded at each call site: it crosses into another app, its layout is decided by the
 * system image rather than by this project, and it is the step most likely to change between API
 * levels. Everything it knows was read off a real picker rather than taken from documentation —
 * see `docs/testing/upgrade-harness-operations.md`.
 *
 * Two behaviours are worth knowing before changing anything here:
 *
 * - **The picker does not open where the file is.** It opens on the *Recent* root, which on a
 *   freshly booted emulator lists nothing at all (`No items`), even though the file is present in
 *   Downloads and passes the caller's MIME filter. Waiting for the filename on the first screen
 *   therefore always times out. The roots drawer must be opened and `Downloads` chosen explicitly.
 * - **Never select a root by the device's own name.** The drawer also lists the device
 *   (`sdk_gphone64_arm64` on the local emulator, something else everywhere else). `Downloads` is
 *   the only label that is stable across devices.
 *
 * @param ui shared waiting and failure-description plumbing
 */
internal class SafDocumentPicker(private val ui: UiSupport) {

    /**
     * Selects [fileName] from the Downloads root, opening the roots drawer if needed.
     *
     * @param fileName exact display name of the file, as pushed by the host
     */
    fun selectFromDownloads(fileName: String) {
        awaitPicker()

        repeat(NAVIGATION_ATTEMPTS) { attempt ->
            ui.device.findObject(By.text(fileName))?.let {
                it.click()
                return
            }
            if (attempt == 0) {
                openDownloadsRoot()
            } else {
                // The drawer can swallow the first tap while it is still animating open, so later
                // attempts re-open it from scratch rather than assuming where the UI got to.
                ui.device.pressBack()
                openDownloadsRoot()
            }
            ui.device.wait(Until.findObject(By.text(fileName)), FILE_TIMEOUT_MS)
        }

        error(
            "'$fileName' never appeared in the Downloads root of the document picker after " +
                "$NAVIGATION_ATTEMPTS attempts. The host pushes it to /sdcard/Download and asks " +
                "MediaProvider to rescan; if it is genuinely on disk, check that the picker's MIME " +
                "filter still accepts it${ui.describeScreen()}",
        )
    }

    private fun awaitPicker() {
        val appeared = ui.device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE)), PICKER_TIMEOUT_MS)
        check(appeared) {
            "the document picker ($DOCUMENTS_UI_PACKAGE) never came to the foreground" +
                ui.describeScreen()
        }
    }

    private fun openDownloadsRoot() {
        ui.awaitObject(By.desc(SHOW_ROOTS_DESC)).click()
        ui.awaitObject(By.text(DOWNLOADS_ROOT)).click()
    }

    private companion object {
        const val DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"

        // Content description of the toolbar's drawer button, and the label of the root that the
        // host pushes fixtures into.
        const val SHOW_ROOTS_DESC = "Show roots"
        const val DOWNLOADS_ROOT = "Downloads"

        const val PICKER_TIMEOUT_MS = 20_000L
        const val FILE_TIMEOUT_MS = 10_000L
        const val NAVIGATION_ATTEMPTS = 3
    }
}
