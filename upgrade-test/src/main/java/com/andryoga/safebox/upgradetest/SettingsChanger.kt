package com.andryoga.safebox.upgradetest

/**
 * Moves the app's settings off their shipped defaults, and checks later that they stayed moved.
 *
 * A preference left at its default survives any migration trivially — including one that drops
 * the preference store entirely — so asserting on defaults after an upgrade proves nothing. These
 * are flipped so that a wiped or misread store shows up as a changed value.
 *
 * Turning auto-backup off matters for more than the upgrade check: while it is on, every password
 * login writes a backup file, and the harness needs exactly one file in the backup folder after
 * each backup step.
 *
 * Only booleans are changed: dragging a slider to a chosen value depends on the device's pixel
 * width.
 *
 * @param ui shared waiting and failure-description plumbing
 * @param app the installed build's own labels, resolved by resource name
 */
internal class SettingsChanger(private val ui: UiSupport, private val app: AppStrings) {

    /**
     * Flips every toggle in [OFF_AFTER_SETUP] and leaves the app on the settings tab.
     *
     * The app must already be signed in and showing the bottom navigation.
     */
    fun applyNonDefaults() {
        ui.clickText(app.label(SETTINGS_TAB))
        OFF_AFTER_SETUP.forEach { turnOff(it, app.label(it)) }
    }

    /**
     * Asserts every toggle in [OFF_AFTER_SETUP] is still off, and leaves the app on the settings
     * tab. Used after the upgrade.
     */
    fun assertNonDefaults() {
        ui.clickText(app.label(SETTINGS_TAB))
        val stillOn = OFF_AFTER_SETUP.filter { name ->
            ui.retryingOnStale { ui.switchBeside(app.label(name)).isChecked }
        }
        logStep("settings still on after the upgrade: $stillOn")
        check(stillOn.isEmpty()) {
            "settings turned off before the upgrade are on again after it, so the preference " +
                "store was reset or misread: $stillOn${ui.describeScreen()}"
        }
    }

    /**
     * Turns one switch off, and proves that it went off.
     *
     * A tap that lands between rows, or on a disabled switch, leaves the screen looking exactly as
     * it did and would otherwise surface much later as an unexplained settings mismatch. Reading
     * the state and tapping are retried as a unit, since a switch animating as the screen settles
     * invalidates the handle between the two.
     *
     * @param resourceName the row's string resource, named in the failure
     * @param label the settings row's visible title, as this build renders it
     */
    private fun turnOff(resourceName: String, label: String) {
        logStep("turn off setting '$resourceName'")
        ui.retryingOnStale {
            val switch = ui.switchBeside(label)
            check(switch.isChecked) {
                "'$label' ($resourceName) was already off before the harness touched it, so " +
                    "toggling it no longer moves the app off its defaults. The shipped default " +
                    "has changed" + ui.describeScreen()
            }
            switch.click()
        }

        // Polled, not read once: the click returns before Compose has recomposed the switch and
        // the accessibility node reflects it, so an immediate read can still see it on.
        ui.awaitCondition(
            SWITCH_TIMEOUT_MS,
            { "'$label' is still on after being tapped" },
        ) { !ui.switchBeside(label).isChecked }
    }

    private companion object {
        const val SETTINGS_TAB = "bottom_nav_settings"
        const val SWITCH_TIMEOUT_MS = 5_000L

        // Both ship enabled. Held as resource names, like every other label the harness matches
        // on, so a copy edit to either row changes nothing here (ADR-0003).
        val OFF_AFTER_SETUP = listOf(
            "settings_privacy_enabled_title",
            "settings_auto_backup_title",
        )
    }
}
