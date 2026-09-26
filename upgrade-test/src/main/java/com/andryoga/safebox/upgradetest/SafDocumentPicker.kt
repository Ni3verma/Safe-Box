package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By
import java.util.regex.Pattern

/**
 * Drives the system document picker (DocumentsUI): picking a backup file the host pushed to
 * Downloads, and granting the app a backup folder.
 *
 * This is the flakiest interaction in the harness, so it lives behind one helper rather than being
 * open-coded at each call site: it crosses into another app, its layout is decided by the system
 * image rather than by this project, and it is the step most likely to change between API levels.
 * Everything it knows was read off a real picker — see `docs/testing/upgrade-harness-operations.md`.
 *
 * Two behaviours are worth knowing before changing anything here:
 *
 * - **The picker does not reliably open where the file is.** It opens on the *Recent* root, which
 *   may list the file the host has just pushed, or may list nothing at all (`No items`) even
 *   though the file is present in Downloads. Both were observed within minutes of each other on
 *   the same emulator on 2026-09-23, so the helper takes the file wherever it finds it and
 *   otherwise opens the roots drawer and chooses `Downloads` explicitly.
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

        repeat(NAVIGATION_ATTEMPTS) {
            if (ui.isPresent(By.text(fileName))) {
                ui.clickObject(By.text(fileName))
                return
            }
            openDownloadsRoot()
            // The wait's own result decides the outcome. Discarding it and re-testing at the top
            // of the next pass means a file that did appear can be missed by a transient hiccup.
            if (ui.findOrNull(By.text(fileName), FILE_TIMEOUT_MS) != null) {
                ui.clickObject(By.text(fileName))
                return
            }
        }

        error(
            "'$fileName' never appeared in the Downloads root of the document picker after " +
                "$NAVIGATION_ATTEMPTS attempts. The host pushes it to /sdcard/Download and asks " +
                "MediaProvider to rescan; if it is genuinely on disk, check that the picker's MIME " +
                "filter still accepts it${ui.describeScreen()}",
        )
    }

    /**
     * Grants the app a tree over [folderName], a directory the host created at the root of shared
     * storage, and confirms the permission dialog that follows.
     *
     * This is the *tree* picker (`ACTION_OPEN_DOCUMENT_TREE`), which behaves nothing like the file
     * picker above. It remembers where it was last left, so the first thing this does is walk back
     * to the root by tapping the leading breadcrumb. And **most obvious directories cannot be
     * granted at all**: Android refuses the root of shared storage and `Download` alike, greying
     * out its own button, which from the outside looks exactly like a tap that did not land. Hence
     * a dedicated directory, created by the host.
     *
     * @param folderName display name of the directory at the root of shared storage
     */
    fun selectFolder(folderName: String) {
        awaitPicker()

        if (ui.isPresent(By.res(BREADCRUMB_ARROW_ID))) {
            ui.clickObject(By.res(BREADCRUMB_TEXT_ID))
        }
        ui.scrollTo(By.text(folderName), ROOT_LISTING_SWIPES).click()

        ui.clickObject(By.text(USE_FOLDER_BUTTON))
        ui.clickObject(By.text(ALLOW_BUTTON))
    }

    private fun awaitPicker() {
        val appeared = ui.findOrNull(By.pkg(DOCUMENTS_UI_PACKAGE), PICKER_TIMEOUT_MS) != null
        check(appeared) {
            "the document picker ($DOCUMENTS_UI_PACKAGE) never came to the foreground" +
                ui.describeScreen()
        }
    }

    /**
     * Navigates to the Downloads root from wherever the picker currently is.
     *
     * Safe to call repeatedly, which is the whole point: it is how every retry re-orients itself.
     * The drawer button is tapped only if it is there, because after a successful attempt the
     * picker is *already* browsing Downloads with the drawer closed.
     *
     * No back press first: measured on 2026-09-23, back from a root with no drawer open dismisses
     * the picker outright, and the retry then failed blaming the drawer. The retry never runs in a
     * passing run, so re-run that experiment if this function changes.
     */
    private fun openDownloadsRoot() {
        if (ui.isPresent(By.desc(SHOW_ROOTS_DESC))) {
            ui.clickObject(By.desc(SHOW_ROOTS_DESC))
        }
        ui.clickObject(By.text(DOWNLOADS_ROOT))
    }

    private companion object {
        // Two images, two package names. Google APIs images ship com.google.android.documentsui;
        // a plain AOSP image ships com.android.documentsui. ATD images ship no DocumentsUI at all,
        // which is a CI configuration error rather than something to widen this for.
        val DOCUMENTS_UI_PACKAGE: Pattern = Pattern.compile("com\\.(google\\.)?android\\.documentsui")

        // Platform UI text, not app labels, so there is no app resource to resolve them from.
        const val SHOW_ROOTS_DESC = "Show roots"
        const val DOWNLOADS_ROOT = "Downloads"

        // Case-insensitive: DocumentsUI updates through Mainline independently of the API level,
        // and Material restyles change button capitalisation. Observed as "USE THIS FOLDER" and
        // "ALLOW" on API 34 (2026-09-23). UI Automator matches a text pattern against the whole
        // text, so neither can match a longer label.
        val USE_FOLDER_BUTTON: Pattern = Pattern.compile("use this folder", Pattern.CASE_INSENSITIVE)
        val ALLOW_BUTTON: Pattern = Pattern.compile("allow", Pattern.CASE_INSENSITIVE)

        // The tree picker's breadcrumb has no stable text - it is named after the device - so it is
        // matched by resource id. The arrow only exists while the picker is below the root, which
        // is exactly the condition for needing to go back up.
        val BREADCRUMB_ARROW_ID: Pattern = breadcrumbId("breadcrumb_arrow")
        val BREADCRUMB_TEXT_ID: Pattern = breadcrumbId("breadcrumb_text")

        const val PICKER_TIMEOUT_MS = 20_000L
        const val FILE_TIMEOUT_MS = 10_000L
        const val NAVIGATION_ATTEMPTS = 3

        // The root of shared storage lists about fifteen standard directories.
        const val ROOT_LISTING_SWIPES = 5

        /**
         * Builds a resource-id matcher that accepts either DocumentsUI package.
         *
         * @param name the id's local name, as it appears after the colon
         * @return a pattern matching that id under either package
         */
        private fun breadcrumbId(name: String): Pattern =
            Pattern.compile("com\\.(google\\.)?android\\.documentsui:id/$name")
    }
}
