package com.andryoga.safebox.ui.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins which build types publish test tags as resource ids, and that the tags themselves stay
 * usable as selectors.
 *
 * The build-type rule is security-relevant — `release` must never expose them — so it is asserted
 * here rather than left to the Gradle configuration, where an `initWith` change could flip it
 * silently.
 */
class TestTagsTest {

    @Test
    fun releaseBuild_shouldNotExposeTestTags() {
        assertThat(TestTags.shouldExposeAsResourceId("release")).isFalse()
    }

    @Test
    fun qaBuild_shouldExposeTestTags() {
        assertThat(TestTags.shouldExposeAsResourceId("qa")).isTrue()
    }

    @Test
    fun debugBuild_shouldExposeTestTags() {
        assertThat(TestTags.shouldExposeAsResourceId("debug")).isTrue()
    }

    @Test
    fun unknownBuildType_shouldNotExposeTestTags() {
        assertThat(TestTags.shouldExposeAsResourceId("benchmark")).isFalse()
        assertThat(TestTags.shouldExposeAsResourceId("")).isFalse()
    }

    @Test
    fun buildTypeMatch_shouldBeCaseSensitive() {
        assertThat(TestTags.shouldExposeAsResourceId("Release")).isFalse()
        assertThat(TestTags.shouldExposeAsResourceId("QA")).isFalse()
    }

    @Test
    fun settingsTags_shouldBeUnique() {
        val tags = listOf(
            TestTags.SETTINGS_PRIVACY_SWITCH,
            TestTags.SETTINGS_AUTO_BACKUP_SWITCH,
            TestTags.SETTINGS_PASSWORD_AFTER_BIOMETRIC_SLIDER,
            TestTags.SETTINGS_AWAY_TIMEOUT_SLIDER,
        )

        assertThat(tags).containsNoDuplicates()
    }
}
