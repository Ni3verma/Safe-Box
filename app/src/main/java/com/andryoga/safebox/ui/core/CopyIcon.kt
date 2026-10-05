package com.andryoga.safebox.ui.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** How long the check mark stays before the glyph reverts to the copy icon. */
private const val COPIED_FEEDBACK_MS = 1_500L
private const val SWAP_INITIAL_SCALE = 0.4f

/**
 * UI-local memory of "the user just copied this": [isCopied] turns on with [markCopied] and turns
 * itself off after [COPIED_FEEDBACK_MS]. Copying again while it is on restarts the timer. Obtain
 * one with [rememberCopiedFlag].
 */
@Stable
class CopiedFlag {
    private var copyCount by mutableIntStateOf(0)

    val isCopied: Boolean
        get() = copyCount > 0

    fun markCopied() {
        copyCount++
    }

    internal val generation: Int
        get() = copyCount

    internal fun reset() {
        copyCount = 0
    }
}

@Composable
fun rememberCopiedFlag(): CopiedFlag {
    val flag = remember { CopiedFlag() }
    LaunchedEffect(flag.generation) {
        if (flag.generation > 0) {
            delay(COPIED_FEEDBACK_MS)
            flag.reset()
        }
    }
    return flag
}

/**
 * Copy glyph that becomes a check mark while [isCopied] is true, each swap popping in from
 * [SWAP_INITIAL_SCALE] with a spring. It is deliberately a single node (the vector swaps on the
 * same `Icon`) rather than an `AnimatedContent`, so the semantics tree always holds exactly one
 * element with [contentDescription] and tests and screen readers never see two copy buttons.
 *
 * @param isCopied Whether to show the confirmation check instead of the copy glyph.
 * @param contentDescription Accessibility label; kept constant across both glyphs because the
 * action the element performs does not change.
 */
@Composable
fun CopyIcon(
    isCopied: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val scale = remember { Animatable(1f) }
    var renderedState by remember { mutableIntStateOf(if (isCopied) 1 else 0) }
    LaunchedEffect(isCopied) {
        val target = if (isCopied) 1 else 0
        if (renderedState == target) return@LaunchedEffect
        renderedState = target
        scale.snapTo(SWAP_INITIAL_SCALE)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    Icon(
        imageVector = if (renderedState == 1) Icons.Filled.Check else Icons.Filled.ContentCopy,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
    )
}
