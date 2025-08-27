package com.andryoga.composeapp.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// Make color lighter by a given factor (0f..1f)
fun Color.lighten(factor: Float = 0.2f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = (hsl[2] + factor).coerceAtMost(1f) // increase lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

// Make color darker by a given factor (0f..1f)
fun Color.darken(factor: Float = 0.2f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = (hsl[2] - factor).coerceAtLeast(0f) // decrease lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

// Complementary color (opposite on color wheel)
fun Color.complementary(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[0] = (hsl[0] + 180f) % 360f // shift hue
    return Color(ColorUtils.HSLToColor(hsl))
}
