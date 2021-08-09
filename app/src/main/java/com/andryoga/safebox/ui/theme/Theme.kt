package com.andryoga.safebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = colorPrimary,
    primaryVariant = colorSecondary,
    secondary = colorAccent
)

private val LightColorPalette = lightColors(
    primary = colorPrimaryDark,
    primaryVariant = colorSecondaryDark,
    secondary = colorAccent
)

@Composable
fun BasicSafeBoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        content = content,
        colors = colors,
        typography = Typography,
        shapes = Shapes
    )
}
