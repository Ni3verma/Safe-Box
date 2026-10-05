package com.andryoga.safebox.ui.core.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Stagger between successive elements of a screen entrance (title, card, hero glyph). */
private const val ENTRANCE_STAGGER_MS = 100

/** Fraction of an element's height it travels while sliding into place on entrance. */
private const val ENTRANCE_SLIDE_FRACTION = 3

/**
 * Visibility state for a one-shot screen entrance: `false` on the first composition of a screen
 * and `true` from the next frame, so `AnimatedVisibility` plays its enter transition exactly once.
 *
 * The "already entered" flag is saved across configuration changes and process death, so rotating
 * the device does not replay the choreography.
 */
@Composable
fun rememberEntranceState(): MutableTransitionState<Boolean> {
    var hasEntered by rememberSaveable { mutableStateOf(false) }
    val state = remember { MutableTransitionState(initialState = hasEntered) }
    LaunchedEffect(Unit) {
        state.targetState = true
        hasEntered = true
    }
    return state
}

/**
 * Entrance for the n-th element of a screen: fades in while rising from a third of its height,
 * delayed by [order] staggers so elements settle one after another, top to bottom.
 *
 * @param order 0 for the first element to appear, 1 for the next, and so on.
 * @param fromTop Slide down from above instead of rising from below, for headers.
 */
fun entranceSlideIn(order: Int = 0, fromTop: Boolean = false): EnterTransition {
    val delay = order * ENTRANCE_STAGGER_MS
    return fadeIn(animationSpec = tween(MotionTokens.DURATION_MEDIUM_MS, delayMillis = delay)) +
        slideInVertically(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_MEDIUM_MS,
                delayMillis = delay,
                easing = FastOutSlowInEasing,
            ),
            initialOffsetY = { height ->
                if (fromTop) -height / ENTRANCE_SLIDE_FRACTION else height / ENTRANCE_SLIDE_FRACTION
            },
        )
}

/**
 * Entrance for a hero glyph: pops from [MotionTokens.POP_IN_INITIAL_SCALE] with a slight
 * overshoot, delayed by [order] staggers.
 */
fun entrancePopIn(order: Int = 0): EnterTransition {
    val delay = order * ENTRANCE_STAGGER_MS
    return fadeIn(animationSpec = tween(MotionTokens.DURATION_MEDIUM_MS, delayMillis = delay)) +
        scaleIn(
            animationSpec = tween(
                durationMillis = MotionTokens.DURATION_MEDIUM_MS,
                delayMillis = delay,
                easing = EaseOutBack,
            ),
            initialScale = MotionTokens.POP_IN_INITIAL_SCALE,
        )
}
