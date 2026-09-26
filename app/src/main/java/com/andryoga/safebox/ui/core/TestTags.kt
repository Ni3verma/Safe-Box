package com.andryoga.safebox.ui.core

/**
 * Stable identifiers for controls that cannot be selected reliably by their own text, and the rule
 * that decides which builds expose them to UI Automator.
 *
 * Text and content description remain the default selectors (see
 * `docs/testing/testing-strategy.md`). A tag exists only where text is not enough:
 * - a settings `Switch` or `Slider` sits beside its label rather than carrying it, so black-box
 *   tests otherwise have to find it by geometry;
 * - a records-list row's title and type chip can only be paired with each other, and told apart
 *   from the filter chips above the list, by their container;
 * - the Backup & Restore tab's **Backup** and **Restore** buttons carry the same text as the
 *   section headings above them.
 *
 * A tag is not faster to look up than text: UI Automator walks the same tree either way. Do not
 * add one where a text selector already works.
 *
 * Compose keeps `Modifier.testTag` inside its own semantics tree. UI Automator, which drives the
 * minified QA APK in the upgrade-test harness, sees a tag only when `testTagsAsResourceId` is
 * set, where it appears as the node's resource id. Each value below is therefore a public
 * contract with that harness: renaming one breaks it. Values are plain string literals, so R8
 * leaves them intact.
 */
object TestTags {
    const val SETTINGS_PRIVACY_SWITCH = "settings_privacy_switch"
    const val SETTINGS_AUTO_BACKUP_SWITCH = "settings_auto_backup_switch"
    const val SETTINGS_PASSWORD_AFTER_BIOMETRIC_SLIDER = "settings_password_after_biometric_slider"
    const val SETTINGS_AWAY_TIMEOUT_SLIDER = "settings_away_timeout_slider"
    const val RECORDS_LIST = "records_list"
    const val RECORD_ROW = "record_row"
    const val RECORD_ROW_TITLE = "record_row_title"
    const val RECORD_ROW_TYPE = "record_row_type"
    const val BACKUP_BUTTON = "backup_button"
    const val RESTORE_BUTTON = "restore_button"

    /**
     * Build types whose UI exposes test tags as resource ids.
     *
     * An allowlist rather than "everything except `release`", so a build type added later stays
     * closed until someone opts it in. `qa` must be listed: it is the build the upgrade-test
     * harness installs, and it copies `release` through `initWith`, so it inherits nothing
     * test-friendly by default.
     */
    private val BUILD_TYPES_EXPOSING_TAGS = setOf("debug", "qa")

    /**
     * Decides whether test tags are published to accessibility services as resource ids.
     *
     * Off in `release`: this is a password vault, and a stable, locale-independent id on every
     * tagged control makes automation by a malicious accessibility service easier to write,
     * for no benefit to users.
     *
     * @param buildType the running build's type, normally `BuildConfig.BUILD_TYPE`
     * @return true only for build types in [BUILD_TYPES_EXPOSING_TAGS]
     */
    fun shouldExposeAsResourceId(buildType: String): Boolean =
        buildType in BUILD_TYPES_EXPOSING_TAGS
}
