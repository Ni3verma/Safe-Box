package com.andryoga.safebox.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.andryoga.safebox.R

val robotoMonoFamily =
    FontFamily(
        Font(R.font.roboto_mono, FontWeight.Normal),
        Font(R.font.roboto_mono_light, FontWeight.Light),
        Font(R.font.roboto_mono_medium, FontWeight.Medium),
    )

val Typography =
    Typography(
        defaultFontFamily = robotoMonoFamily,
    )
