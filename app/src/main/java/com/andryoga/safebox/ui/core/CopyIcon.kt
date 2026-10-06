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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.andryoga.safebox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the check mark stays before the glyph reverts to the copy icon. */
private const val COPIED_FEEDBACK_MS = 1_500L
private const val SWAP_INITIAL_SCALE = 0.4f

/**
 * UI-local memory of "the user just copied this": [isCopied] turns on with [markCopied] and turns
 * itself off after [COPIED_FEEDBACK_MS]. Copying again while it is on restarts the timer. Obtain
 * one with [rememberCopiedFlag], which scopes the timer to the composition.
 */
@Stable
class CopiedFlag internal constructor(private val scope: CoroutineScope) {
    var isCopied: Boolean by mutableStateOf(false)
        private set
    private var revertJob: Job? = null

    fun markCopied() {
        isCopied = true
        revertJob?.cancel()
        revertJob = scope.launch {
            delay(COPIED_FEEDBACK_MS)
            isCopied = false
        }
    }
}

/** A [CopiedFlag] whose revert timer is cancelled when the caller leaves the composition. */
@Composable
fun rememberCopiedFlag(): CopiedFlag {
    val scope = rememberCoroutineScope()
    return remember { CopiedFlag(scope) }
}

/**
 * Copy glyph that becomes a check mark while [isCopied] is true, each swap popping in from
 * [SWAP_INITIAL_SCALE] with a spring. It is deliberately a single node (the vector swaps on the
 * same `Icon`) rather than an `AnimatedContent`, so the semantics tree always holds exactly one
 * element with [contentDescription] and tests and screen readers never see two copy buttons. While
 * the check is shown the node also carries a "Copied" state description, which is how screen
 * readers learn about the swap.
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
    // Tracks the glyph on screen separately from isCopied so the first composition draws the
    // right glyph at full size and only later changes pop in.
    var showsCheck by remember { mutableStateOf(isCopied) }
    LaunchedEffect(isCopied) {
        if (showsCheck == isCopied) return@LaunchedEffect
        showsCheck = isCopied
        scale.snapTo(SWAP_INITIAL_SCALE)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    val copiedState = stringResource(R.string.state_copied)
    Icon(
        imageVector = if (showsCheck) Icons.Filled.Check else Icons.Filled.ContentCopy,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .semantics { if (showsCheck) stateDescription = copiedState }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}
