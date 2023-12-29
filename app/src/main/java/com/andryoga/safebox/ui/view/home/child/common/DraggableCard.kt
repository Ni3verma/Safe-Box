package com.andryoga.safebox.ui.view.home.child.common

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

const val ANIMATION_DURATION = 500
const val MIN_DRAG = 30

@Composable
fun DraggableCard(
    isRevealed: Boolean,
    cardHeight: Dp = 70.dp,
    cardOffset: Float = 56f.dp(),
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { mutableStateOf(0f) }
    val transitionState =
        remember {
            MutableTransitionState(isRevealed).apply {
                targetState = !isRevealed
            }
        }
    val transition = updateTransition(transitionState, "cardTransition")
    val cardBgColor by transition.animateColor(
        label = "cardBgColorTransition",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = {
            if (isRevealed) Color.LightGray else Color.White
        },
    )
    val offsetTransition by transition.animateFloat(
        label = "cardOffsetTransition",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = { if (isRevealed) cardOffset - offsetX.value else -offsetX.value },
    )
    val cardElevation by transition.animateDp(
        label = "cardElevation",
        transitionSpec = { tween(durationMillis = ANIMATION_DURATION) },
        targetValueByState = { if (isRevealed) 5.dp else 0.dp },
    )

    Card(
        modifier =
            Modifier
                .padding(start = 4.dp, top = 8.dp)
                .fillMaxWidth()
                .height(cardHeight)
                .offset { IntOffset((offsetX.value - offsetTransition).roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount <= -MIN_DRAG) {
                            onExpand()
                        } else if (dragAmount > MIN_DRAG) {
                            onCollapse()
                        }
                    }
                },
        backgroundColor = cardBgColor,
        shape = RoundedCornerShape(0.dp),
        elevation = cardElevation,
        content = content,
    )
}
