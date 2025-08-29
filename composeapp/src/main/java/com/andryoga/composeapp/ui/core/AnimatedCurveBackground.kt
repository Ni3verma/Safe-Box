package com.andryoga.composeapp.ui.core

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import com.andryoga.composeapp.ui.utils.darken
import com.andryoga.composeapp.ui.utils.lighten

@Composable
fun AnimatedCurveBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")

    val waveShift by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveShift"
    )

    // Infinite animation for gradient offset
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f, // arbitrary shift distance
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )

    val primaryColor = colorScheme.primary
    val secondaryColor = colorScheme.secondary


    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                primaryColor.lighten(0.3f),
                primaryColor,
                primaryColor.darken(0.3f),
                secondaryColor.lighten(0.3f),
                secondaryColor,
                secondaryColor.darken(0.3f)
            ),
            start = Offset(0f + gradientShift, 0f),
            end = Offset(width + gradientShift, height)
        )


        // Wave Path
        val path1 = Path().apply {
            moveTo(0f, height * 0.3f)
            quadraticTo(
                width * 0.5f,
                height * 0.15f + waveShift,
                width,
                height * 0.3f
            )
            lineTo(width, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path1, brush = gradientBrush)
    }
}
