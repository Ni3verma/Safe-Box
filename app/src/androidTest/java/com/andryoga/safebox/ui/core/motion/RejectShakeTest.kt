package com.andryoga.safebox.ui.core.motion

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andryoga.safebox.test.fakes.FakeHapticFeedback
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [rejectShake]: the gesture must fire exactly once per rejection, never for a
 * rejection the screen was created with (rotation), and must leave the element where it started.
 */
@RunWith(AndroidJUnit4::class)
class RejectShakeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val haptic = FakeHapticFeedback()

    @Test
    fun firstComposition_shouldNotPerformHapticForAnAlreadySeenTrigger() {
        setShakingContent(initialTrigger = 3)
        composeTestRule.waitForIdle()

        assertThat(haptic.performed).isEmpty()
    }

    @Test
    fun triggerIncrement_shouldPerformRejectHapticOncePerRejection() {
        val trigger = setShakingContent(initialTrigger = 0)

        composeTestRule.runOnIdle { trigger.intValue = 1 }
        composeTestRule.waitForIdle()
        assertThat(haptic.performed).containsExactly(HapticFeedbackType.Reject)

        composeTestRule.runOnIdle { trigger.intValue = 2 }
        composeTestRule.waitForIdle()
        assertThat(haptic.performed)
            .containsExactly(HapticFeedbackType.Reject, HapticFeedbackType.Reject)
    }

    @Test
    fun triggerIncrement_shouldMoveTheElementAndReturnItToRest() {
        val trigger = setShakingContent(initialTrigger = 0)
        val restingBounds = composeTestRule.onNodeWithText(LABEL).getBoundsInRoot()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnIdle { trigger.intValue = 1 }
        var moved = false
        // 40 frames is comfortably longer than the shake, so this samples the whole gesture.
        repeat(40) {
            composeTestRule.mainClock.advanceTimeByFrame()
            if (composeTestRule.onNodeWithText(LABEL).getBoundsInRoot() != restingBounds) {
                moved = true
            }
        }
        assertThat(moved).isTrue()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertThat(composeTestRule.onNodeWithText(LABEL).getBoundsInRoot()).isEqualTo(restingBounds)
    }

    private fun setShakingContent(initialTrigger: Int): MutableIntState {
        val trigger = mutableIntStateOf(initialTrigger)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                Text(text = LABEL, modifier = Modifier.rejectShake(trigger.intValue))
            }
        }
        return trigger
    }

    companion object {
        private const val LABEL = "shaking element"
    }
}
