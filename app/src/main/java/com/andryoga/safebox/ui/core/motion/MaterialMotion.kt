package com.andryoga.safebox.ui.core.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith

/**
 * Material motion duration tokens shared by every animation in the app, so screens, dialogs and
 * list content all move at the same tempo. Navigation transitions, content swaps and reveal
 * animations must take their timing from here rather than hard-coding durations.
 */
object MotionTokens {
    /** Material `medium2`: the default length of a screen or content transition. */
    const val DURATION_MEDIUM_MS = 300

    /** Material `short2`: how long outgoing content takes to fade out in a fade through. */
    const val DURATION_OUTGOING_MS = 90

    /** Remainder of [DURATION_MEDIUM_MS] after the outgoing fade: the incoming content's fade in. */
    const val DURATION_INCOMING_MS = DURATION_MEDIUM_MS - DURATION_OUTGOING_MS

    /** Incoming content in a fade through grows from this scale to full size. */
    const val FADE_THROUGH_INITIAL_SCALE = 0.92f

    /** Elements that pop into view (icons, hero glyphs) start from this scale. */
    const val POP_IN_INITIAL_SCALE = 0.6f
}

/**
 * Incoming half of the Material **fade through** pattern: a fade in that starts once the outgoing
 * content has disappeared, optionally combined with a subtle scale up from
 * [MotionTokens.FADE_THROUGH_INITIAL_SCALE].
 *
 * @param withScale Drop the scale when the incoming and outgoing content share their layout
 * (for example two states of the same list) so elements that exist on both sides stay anchored.
 */
fun fadeThroughEnter(withScale: Boolean = true): EnterTransition {
    val fade = fadeIn(animationSpec = incomingSpec())
    return if (withScale) {
        fade + scaleIn(
            animationSpec = incomingSpec(),
            initialScale = MotionTokens.FADE_THROUGH_INITIAL_SCALE,
        )
    } else {
        fade
    }
}

/** Outgoing half of the fade through pattern: a quick fade out that clears the stage first. */
fun fadeThroughExit(): ExitTransition = fadeOut(animationSpec = outgoingSpec())

/**
 * Complete fade through for an `AnimatedContent` `transitionSpec`.
 *
 * @param withScale See [fadeThroughEnter].
 */
fun fadeThrough(withScale: Boolean = true): ContentTransform =
    fadeThroughEnter(withScale) togetherWith fadeThroughExit()

/** Timing of the incoming content in every fade through: waits for the outgoing fade, then eases in. */
fun incomingSpec() = tween<Float>(
    durationMillis = MotionTokens.DURATION_INCOMING_MS,
    delayMillis = MotionTokens.DURATION_OUTGOING_MS,
    easing = LinearOutSlowInEasing,
)

/** Timing of the outgoing content in every fade through: a quick ease out that clears the stage. */
fun outgoingSpec() = tween<Float>(
    durationMillis = MotionTokens.DURATION_OUTGOING_MS,
    easing = FastOutLinearInEasing,
)
