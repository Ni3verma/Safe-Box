package com.andryoga.safebox.ui.core.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset

/**
 * Single-line text whose value rolls like an odometer when it changes: the old value slides up
 * and out while the new one slides in from below, clipped to the line's own bounds. Used for the
 * rotating TOTP code, where a hard swap every 30 seconds goes unnoticed and a roll says "this is
 * a new code".
 *
 * Parameters mirror the subset of [Text] the call sites need.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = { rollUp() },
        label = "rollingText",
    ) { value ->
        Text(
            text = value,
            color = color,
            style = style,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = 1,
        )
    }
}

/** Odometer roll: new value enters from below while the old one leaves upwards, both fading. */
private fun rollUp(): ContentTransform {
    val spec = tween<Float>(MotionTokens.DURATION_MEDIUM_MS, easing = FastOutSlowInEasing)
    val slideSpec = tween<IntOffset>(MotionTokens.DURATION_MEDIUM_MS, easing = FastOutSlowInEasing)
    val enter = slideInVertically(animationSpec = slideSpec) { height -> height } + fadeIn(spec)
    val exit = slideOutVertically(animationSpec = slideSpec) { height -> -height } + fadeOut(spec)
    // clip = true keeps the travelling glyphs inside the line so they never overlap neighbours.
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = true),
    )
}
