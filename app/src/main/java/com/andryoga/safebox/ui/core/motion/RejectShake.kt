package com.andryoga.safebox.ui.core.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

private const val SHAKE_DURATION_MS = 450
private val SHAKE_AMPLITUDE = 12.dp

/**
 * The lock-screen "wrong input" gesture: a horizontal shake that decays to rest, paired with the
 * platform's reject haptic. Every change of [trigger] plays it once, so callers pass a counter
 * that grows with each rejected attempt instead of a boolean that would only fire the first time.
 *
 * Returns a modifier to put on the element that should shake (typically the offending text
 * field). The value of [trigger] seen at first composition is treated as already acknowledged, so
 * a screen recreated after rotation with a non-zero counter does not replay an old rejection.
 *
 * @param trigger Monotonic counter of rejections.
 */
@Composable
fun rememberRejectShake(trigger: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val amplitudePx = with(LocalDensity.current) { SHAKE_AMPLITUDE.toPx() }
    val acknowledgedTrigger = remember { trigger }
    LaunchedEffect(trigger) {
        if (trigger == acknowledgedTrigger) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = SHAKE_DURATION_MS
                -amplitudePx at 50
                amplitudePx at 100
                -amplitudePx * 0.7f at 160
                amplitudePx * 0.7f at 220
                -amplitudePx * 0.4f at 280
                amplitudePx * 0.4f at 340
                -amplitudePx * 0.15f at 400
            },
        )
    }
    return Modifier.graphicsLayer { translationX = offset.value }
}
