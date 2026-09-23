package com.andryoga.safebox.upgradetest

/**
 * Moves the app's settings off their shipped defaults, so the upgrade has something to lose.
 *
 * A preference left at its default survives any migration trivially — including one that drops the
 * preference store entirely and rebuilds it — so asserting on defaults after an upgrade proves
 * nothing at all. These are flipped precisely so that a wiped or misread store shows up as a
 * changed value rather than as a value that happens to match.
 *
 * Only booleans are changed. The screen also carries two sliders, and dragging one to a chosen
 * value depends on the pixel width of the device; Phase A has to produce a byte-identical oracle
 * run after run, and a drag cannot promise that. If a slider is ever needed, set it through an
 * accessibility action rather than a swipe.
 *
 * @param ui shared waiting and failure-description plumbing
 */
internal class SettingsChanger(private val ui: UiSupport) {

    /**
     * Flips every toggle in [OFF_BY_DEFAULT_AFTER_THIS] and leaves the app on the settings tab.
     *
     * The app must already be signed in and showing the bottom navigation.
     */
    fun applyNonDefaults() {
        ui.clickText(SETTINGS_TAB)
        OFF_BY_DEFAULT_AFTER_THIS.forEach(::turnOff)
    }

    /**
     * Turns one switch off, and proves that it went off.
     *
     * The check is not ceremony: a tap that lands between rows, or on a row whose switch is
     * disabled, leaves the screen looking exactly as it did and would otherwise be discovered much
     * later as an oracle mismatch with no explanation attached.
     *
     * Reading the state and then tapping are two round trips against one handle, so the pair is
     * retried as a unit — a switch that animates as the screen settles invalidates the handle in
     * between, which would otherwise surface as a crash rather than as the timing blip it is.
     *
     * @param label the settings row's visible title
     */
    private fun turnOff(label: String) {
        ui.retryingOnStale {
            val switch = ui.switchBeside(label)
            check(switch.isChecked) {
                "'$label' was already off before Phase A touched it, so toggling it no longer " +
                    "moves the app off its defaults. The baseline's shipped default has changed" +
                    ui.describeScreen()
            }
            switch.click()
        }

        check(!ui.retryingOnStale { ui.switchBeside(label).isChecked }) {
            "'$label' is still on after being tapped${ui.describeScreen()}"
        }
    }

    /**
     * Not private because [VaultOracle] reads the same two rows back out. One list, so the state
     * Phase A changes and the state the oracle records cannot drift apart.
     */
    companion object {
        private const val SETTINGS_TAB = "Settings"

        /**
         * Both ship enabled, and both are read back by the oracle.
         *
         * Turning auto-backup off is doubly useful: every password login writes a backup file while
         * it is on, which would put a "last taken on <timestamp>" line — a value that changes every
         * run — into the state Phase A is supposed to reproduce exactly.
         */
        val OFF_BY_DEFAULT_AFTER_THIS = listOf(
            "Privacy mode",
            "Auto-backup on login",
        )
    }
}
