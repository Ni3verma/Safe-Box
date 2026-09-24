package com.andryoga.safebox.upgradetest

import androidx.test.uiautomator.By
import java.util.regex.Pattern

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
 * - **The picker does not reliably open where the file is.** It opens on the *Recent* root, which
 *   may list the fixture the host has just pushed, or may list nothing at all (`No items`) even
 *   though the file is present in Downloads and passes the caller's MIME filter. Both were
 *   observed within minutes of each other on the same emulator on 2026-09-23, so neither may be
 *   assumed: the helper takes the file wherever it finds it and otherwise opens the roots drawer
 *   and chooses `Downloads` explicitly. The practical consequence is that the drawer path is
 *   *skipped entirely* in many passing runs, so it gets far less coverage than the pass rate
 *   suggests.
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

    private fun awaitPicker() {
        val appeared = ui.findOrNull(By.pkg(DOCUMENTS_UI_PACKAGE), PICKER_TIMEOUT_MS) != null
        check(appeared) {
            "the document picker ($DOCUMENTS_UI_PACKAGE) never came to the foreground" +
                ui.describeScreen()
        }
    }

    /**
     * Grants the app a tree over [folderName], a directory the host created at the root of shared
     * storage, and confirms the permission dialog that follows.
     *
     * This is the *tree* picker (`ACTION_OPEN_DOCUMENT_TREE`), which behaves nothing like the file
     * picker above and has two rules of its own.
     *
     * It opens on the storage root rather than on Recent, and it remembers where it was last left,
     * so the first thing this does is walk back to the root by tapping the leading breadcrumb. Not
     * doing that makes the interaction depend on whatever a previous run happened to browse to.
     *
     * More importantly, **most obvious directories cannot be granted at all**. Android refuses the
     * root of shared storage and `Download` alike — the picker greys out its own button and shows
     * "Can't use this folder / To protect your privacy, choose another folder", which from the
     * outside looks exactly like a tap that did not land. Hence a dedicated directory, created by
     * the host so the test never has to drive the picker's "create folder" flow.
     *
     * @param folderName display name of the directory at the root of shared storage
     */
    fun selectFolder(folderName: String) {
        awaitPicker()

        if (ui.isPresent(By.res(BREADCRUMB_ARROW_ID))) {
            ui.clickObject(By.res(BREADCRUMB_TEXT_ID))
        }

        checkNotNull(ui.scrollToText(folderName)) {
            "'$folderName' is not listed at the root of shared storage in the document picker. " +
                "scripts/run-upgrade-test.sh creates it; if it is genuinely on disk, the picker " +
                "is probably still inside a subdirectory${ui.describeScreen()}"
        }
        ui.clickText(folderName)

        ui.clickText(USE_FOLDER_BUTTON)
        ui.clickText(ALLOW_BUTTON)
    }

    /**
     * Navigates to the Downloads root from wherever the picker currently is.
     *
     * Safe to call repeatedly, which is the whole point: it is how every retry attempt re-orients
     * itself. The drawer button is tapped only if it is there, because after a successful attempt
     * the picker is *already* browsing Downloads with the drawer closed, and the toolbar is the
     * only thing that distinguishes that from the drawer being open.
     *
     * The retry used to press back first, on the theory that the drawer might have swallowed the
     * tap while animating. That is the wrong reset for the state the retry is actually in: attempt
     * 0 normally reaches Downloads and merely fails to *see* the file yet, and back from a root
     * with no drawer open dismisses the picker outright.
     *
     * Measured rather than argued, on 2026-09-23, by forcing attempt 0 to navigate and then
     * decline to look for the file. With the back press, Phase A failed with
     * `could not find BySelector [DESC='Show roots'] within 15000ms` and a hierarchy dump whose
     * root package was `com.andryoga.safebox.qa` — the picker was gone and the message blamed the
     * drawer. Without it, the same forced retry recovered and produced the usual oracle. Note that
     * the retry never runs in a passing run, so neither the defect nor this fix is covered by the
     * suite; re-run that experiment if this function changes again.
     */
    private fun openDownloadsRoot() {
        if (ui.isPresent(By.desc(SHOW_ROOTS_DESC))) {
            ui.clickObject(By.desc(SHOW_ROOTS_DESC))
        }
        ui.clickObject(By.text(DOWNLOADS_ROOT))
    }

    private companion object {
        // Two images, two package names. The local emulator has Google APIs and ships
        // com.google.android.documentsui; a plain AOSP image, which is what CI runs, ships
        // com.android.documentsui. Pinning either one makes the picker invisible on the other
        // half of the fleet - awaitPicker would burn its 20 s and report that the picker never
        // opened, on a device where it opened perfectly.
        //
        // A third case is not a package name at all: ATD images ship no DocumentsUI, so the
        // intent lands on com.android.fakesystemapp and no pattern can match. That is a CI
        // configuration error rather than something to widen this regex for - see the target
        // comment in .github/workflows/upgrade-test.yml.
        val DOCUMENTS_UI_PACKAGE: Pattern = Pattern.compile("com\\.(google\\.)?android\\.documentsui")

        // Content description of the toolbar's drawer button, and the label of the root that the
        // host pushes fixtures into.
        const val SHOW_ROOTS_DESC = "Show roots"
        const val DOWNLOADS_ROOT = "Downloads"

        // The tree picker's breadcrumb has no stable text - it is named after the device - so it is
        // matched by resource id. The arrow only exists while the picker is below the root, which
        // is exactly the condition for needing to go back up. Same two package names as above.
        val BREADCRUMB_ARROW_ID: Pattern = breadcrumbId("breadcrumb_arrow")
        val BREADCRUMB_TEXT_ID: Pattern = breadcrumbId("breadcrumb_text")
        const val USE_FOLDER_BUTTON = "USE THIS FOLDER"
        const val ALLOW_BUTTON = "ALLOW"

        const val PICKER_TIMEOUT_MS = 20_000L
        const val FILE_TIMEOUT_MS = 10_000L
        const val NAVIGATION_ATTEMPTS = 3

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
