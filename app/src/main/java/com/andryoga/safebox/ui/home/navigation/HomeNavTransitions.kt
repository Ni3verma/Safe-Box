package com.andryoga.safebox.ui.home.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlin.math.sign

/** Length of every transition in the home graph: Material's `medium2` duration token. */
const val HOME_NAV_TRANSITION_DURATION_MS = 300

/** The outgoing screen is fully gone after this; the incoming one starts fading in at that moment. */
private const val OUTGOING_FADE_DURATION_MS = 90
private const val INCOMING_FADE_DURATION_MS =
    HOME_NAV_TRANSITION_DURATION_MS - OUTGOING_FADE_DURATION_MS
private const val FADE_THROUGH_INITIAL_SCALE = 0.92f
private val SHARED_AXIS_SLIDE_DISTANCE = 30.dp

typealias HomeNavEnterTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
typealias HomeNavExitTransition =
    AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * Motion for the home `NavHost`, following the Material transition patterns.
 *
 * - Between the bottom-navigation tabs (peers) the pattern is **fade through**: the outgoing
 *   screen fades out quickly, then the incoming one fades in while scaling up from 92%.
 * - Into and out of a deeper destination (record detail or create screen, QR scanner) the pattern
 *   is **shared axis X**: the same fades plus a 30 dp horizontal slide, forwards on push and
 *   reversed on pop, so the direction says whether the user is going deeper or coming back.
 *
 * The predictive back gesture scrubs [popEnter] / [popExit], so a half-finished swipe shows the
 * same motion as a completed one. All six `NavHost` slots are pinned to these values: Navigation
 * Compose 2.10 gave the gesture its own defaults (`scaleOut(0.7f)` for the leaving screen, a spring
 * fade for the returning one), and a host that pins only the button-driven slots changes look
 * whenever the library's defaults do.
 *
 * Obtain an instance with [rememberHomeNavTransitions]; the slide distance depends on density.
 */
@Immutable
class HomeNavTransitions internal constructor(
    private val slideDistancePx: Int,
) {
    val enter: HomeNavEnterTransition = {
        if (isHierarchical()) sharedAxisEnter(SlideDirection.Start) else fadeThroughEnter()
    }
    val exit: HomeNavExitTransition = {
        if (isHierarchical()) sharedAxisExit(SlideDirection.Start) else fadeThroughExit()
    }
    val popEnter: HomeNavEnterTransition = {
        if (isHierarchical()) sharedAxisEnter(SlideDirection.End) else fadeThroughEnter()
    }
    val popExit: HomeNavExitTransition = {
        if (isHierarchical()) sharedAxisExit(SlideDirection.End) else fadeThroughExit()
    }

    /** A transition is hierarchical unless both of its ends are bottom-navigation tabs. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.isHierarchical(): Boolean =
        !(
            initialState.destination.isHomeTopLevelRoute() &&
                targetState.destination.isHomeTopLevelRoute()
            )

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisEnter(
        towards: SlideDirection,
    ): EnterTransition =
        slideIntoContainer(
            towards = towards,
            animationSpec = tween(HOME_NAV_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            // The lambda receives the offset of a full-width slide, signed for the direction (and
            // already mirrored for RTL). Keep the sign, replace the magnitude.
            initialOffset = { fullSlide -> fullSlide.sign * slideDistancePx },
        ) + incomingFade()

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.sharedAxisExit(
        towards: SlideDirection,
    ): ExitTransition =
        slideOutOfContainer(
            towards = towards,
            animationSpec = tween(HOME_NAV_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
            targetOffset = { fullSlide -> fullSlide.sign * slideDistancePx },
        ) + outgoingFade()

    private fun fadeThroughEnter(): EnterTransition =
        incomingFade() + scaleIn(
            animationSpec = incomingSpec(),
            initialScale = FADE_THROUGH_INITIAL_SCALE,
        )

    private fun fadeThroughExit(): ExitTransition = outgoingFade()

    private fun incomingFade(): EnterTransition = fadeIn(animationSpec = incomingSpec())

    private fun outgoingFade(): ExitTransition =
        fadeOut(animationSpec = tween(OUTGOING_FADE_DURATION_MS, easing = FastOutLinearInEasing))

    private fun incomingSpec() = tween<Float>(
        durationMillis = INCOMING_FADE_DURATION_MS,
        delayMillis = OUTGOING_FADE_DURATION_MS,
        easing = LinearOutSlowInEasing,
    )
}

/**
 * Builds the home graph transitions for the current density and keeps them across recompositions.
 */
@Composable
fun rememberHomeNavTransitions(): HomeNavTransitions {
    val slideDistancePx = with(LocalDensity.current) { SHARED_AXIS_SLIDE_DISTANCE.roundToPx() }
    return remember(slideDistancePx) { HomeNavTransitions(slideDistancePx) }
}
