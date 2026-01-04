package com.andryoga.safebox.ui.core

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PulseButton(
    @StringRes textResId: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1.0f) }
    val elevation = remember { Animatable(1.dp.value) }

    LaunchedEffect(enabled) {
        if (enabled) {
            launch {
                scale.animateTo(1.1f, spring(stiffness = 500f))
                scale.animateTo(1.0f, spring(stiffness = 500f))
            }
            launch {
                elevation.animateTo(5f, spring(stiffness = 500f))
                elevation.animateTo(1f, spring(stiffness = 500f))
            }
        }
    }

    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = elevation.value.dp
        )
    ) {
        Text(stringResource(textResId))
    }
}