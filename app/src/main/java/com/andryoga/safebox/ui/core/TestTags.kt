package com.andryoga.safebox.ui.core

/**
 * Stable identifiers for controls that have no text or content description of their own, and
 * the rule that decides which builds expose them to UI Automator.
 *
 * Text and content description remain the default selectors (see
 * `docs/testing/testing-strategy.md`). A tag exists only where there is nothing else to select
 * by — a settings `Switch` or `Slider` sits beside its label rather than carrying it, so
 * black-box tests otherwise have to find it by geometry.
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
