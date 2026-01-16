package com.andryoga.safebox.ui.previewHelper

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light Mode",
    showBackground = true
)
@Preview(
    uiMode = UI_MODE_NIGHT_YES,
    name = "Night Mode",
    showBackground = true,
)
annotation class LightDarkModePreview
