package com.andryoga.safebox.ui.core

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.common.CommonConstants
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Circular countdown indicator displaying remaining seconds and a radial progress ring.
 *
 * The arc sweeps continuously between ticks rather than stepping once per second, so only the
 * seconds label changes discretely.
 *
 * Automatically shifts to [MaterialTheme.colorScheme.error] when [remainingSeconds] falls
 * to 5 seconds or below, providing an intuitive visual expiration warning.
 *
 * @param remainingSeconds Number of seconds remaining in the current rotation cycle.
 * @param totalSeconds Total duration of the time step period (typically 30 seconds).
 * @param modifier Composable modifier for sizing and positioning.
 * @param size Outer dimension size of the countdown ring.
 * @param strokeWidth Thickness of the progress indicator arc.
 */
@Composable
fun CircularCountdownRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    strokeWidth: Dp = 3.dp,
) {
    val period = totalSeconds.coerceAtLeast(1)
    val progress = (remainingSeconds.coerceIn(0, period).toFloat() / period)
    val nextProgress = ((remainingSeconds - 1).coerceIn(0, period).toFloat() / period)
    val animatedProgress = remember { Animatable(progress) }
    LaunchedEffect(progress, nextProgress) {
        // snapping first keeps a rollover instant, instead of sweeping backwards for a whole
        // second, and re-anchors the arc to the tick in case the previous sweep was cut short.
        animatedProgress.snapTo(progress)
        // sweeping towards what the next tick will report keeps the arc in step with the label.
        animatedProgress.animateTo(
            targetValue = nextProgress,
            animationSpec = tween(
                durationMillis = CommonConstants.TIME_1_SECOND.toInt(),
                easing = LinearEasing,
            ),
        )
    }

    val targetColor = if (remainingSeconds <= 5) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ringColor by animateColorAsState(
        targetValue = targetColor,
        label = "countdown_color_anim",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        CircularProgressIndicator(
            // read inside the lambda so the sweep stays a draw phase change and never recomposes
            // the surrounding record row every frame.
            progress = { animatedProgress.value },
            modifier = Modifier.size(size),
            color = ringColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = strokeWidth,
        )
        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ringColor,
        )
    }
}

@LightDarkModePreview
@Composable
private fun CircularCountdownRingNormalPreview() {
    SafeBoxTheme {
        CircularCountdownRing(
            remainingSeconds = 24,
            totalSeconds = 30,
        )
    }
}

@LightDarkModePreview
@Composable
private fun CircularCountdownRingExpiringPreview() {
    SafeBoxTheme {
        CircularCountdownRing(
            remainingSeconds = 4,
            totalSeconds = 30,
        )
    }
}
