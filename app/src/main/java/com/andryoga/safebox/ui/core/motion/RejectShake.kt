package com.andryoga.safebox.ui.core.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val SHAKE_DURATION_MS = 450
private val SHAKE_AMPLITUDE = 12.dp

/**
 * The lock-screen "wrong input" gesture: a horizontal shake that decays to rest, paired with the
 * platform's reject haptic. Every change of [trigger] plays it once, so callers pass a counter
 * that grows with each rejected attempt instead of a boolean that would only fire the first time.
 *
 * Put it on the element that should shake (typically the offending text field). The value of
 * [trigger] the modifier is created with is treated as already acknowledged, so a screen
 * recreated after rotation with a non-zero counter does not replay an old rejection.
 *
 * @param trigger Monotonic counter of rejections.
 */
fun Modifier.rejectShake(trigger: Int): Modifier = this then RejectShakeElement(trigger)

private data class RejectShakeElement(
    private val trigger: Int,
) : ModifierNodeElement<RejectShakeNode>() {
    override fun create() = RejectShakeNode(trigger)

    override fun update(node: RejectShakeNode) = node.onTrigger(trigger)

    override fun InspectorInfo.inspectableProperties() {
        name = "rejectShake"
        properties["trigger"] = trigger
    }
}

/**
 * Owns the shake animation and translates its content by the current offset. The offset is read
 * inside the placement layer block, so each animation frame invalidates only the layer, never
 * layout.
 */
private class RejectShakeNode(
    private var acknowledgedTrigger: Int,
) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {
    private val offset = Animatable(0f)
    private var shake: Job? = null

    fun onTrigger(trigger: Int) {
        if (trigger == acknowledgedTrigger) return
        acknowledgedTrigger = trigger
        currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.Reject)
        val amplitudePx = with(requireDensity()) { SHAKE_AMPLITUDE.toPx() }
        shake?.cancel()
        shake = coroutineScope.launch {
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
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) { translationX = offset.value }
        }
    }
}
